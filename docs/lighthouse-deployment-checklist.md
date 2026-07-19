# Lighthouse 低成本部署清单

这份清单对应当前项目的低成本上线方案：

- 微信小程序前端
- Lighthouse 轻量应用服务器
- Docker Compose
- Spring Boot 后端
- Supabase PostgreSQL
- Nginx + HTTPS 反向代理

它适合你当前这个阶段：

- 已经有完整的小程序和 Java 后端
- 暂时不想继续走云托管
- 预算有限，但又想保留正式上线能力

## 1. 这条路线为什么适合当前项目

当前后端已经具备这些条件：

- 已有 `Dockerfile`
- 已有 `release` 环境变量模板
- 已有 MySQL 表结构
- 已有微信登录、流式输出、OCR、会员、历史记录等服务端代码

因此最省改动的低成本形态不是重写成云函数，而是：

1. 用一台 Lighthouse 承载 `nginx + backend`
2. 让小程序继续通过 `https://你的域名/api/...` 访问
3. 先不开支付
4. OCR 先按需开启

## 2. 服务器怎么买

首版建议：

1. 地域：`上海`
2. 系统：`Ubuntu 22.04 LTS`
3. 规格：`2核2G`
4. 系统盘：`40GB` 或 `50GB`

这套规格适合：

- 首次上线
- 小范围真实用户测试
- 作文生成和批改量还不大

如果你后面发现：

- OCR 使用变多
- 高峰时响应慢
- 用户量明显增加

再升级到 `2核4G` 即可。

## 3. 域名和证书怎么配

建议单独给 API 一个子域名，例如：

- `api.your-domain.com`

你需要完成：

1. 域名已备案
2. 域名解析到 Lighthouse 公网 IP
3. 申请 HTTPS 证书

当前仓库里的小程序正式配置仍需要真实 HTTPS 域名：

- 文件：`/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-miniapp/utils/config.js`
- 你后面只需要把 `release.apiBaseUrl` 改成真实地址，例如 `https://api.your-domain.com`

## 4. 服务器第一次初始化

登录服务器后建议先做：

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin certbot
sudo systemctl enable docker
sudo systemctl start docker
```

如果 `docker compose version` 有输出，说明 Compose 插件可用。

## 5. 把项目传到服务器

建议把后端目录放到：

```text
/opt/gaokao-essay-backend
```

只需要上传这个目录：

- `gaokao-essay-backend`

不需要把小程序也一起丢到服务器。

## 6. 项目里已经给你准备好的部署文件

当前仓库新增了这几份 Lighthouse 资产：

- `deploy/lighthouse/docker-compose.yml`
- `deploy/lighthouse/.env.example`
- `deploy/lighthouse/nginx/default.conf.template`

你在服务器上要做的是：

```bash
cd /opt/gaokao-essay-backend/deploy/lighthouse
cp .env.example .env
```

然后编辑 `.env`。

## 7. 先填 `.env`

至少要改这些值：

1. `API_DOMAIN`
2. `GAOKAO_AUTH_TOKEN_SECRET`
3. `GAOKAO_DATABASE_URL`
4. `GAOKAO_DATABASE_USERNAME`
5. `GAOKAO_DATABASE_PASSWORD`
6. `WECHAT_APP_ID`
7. `WECHAT_APP_SECRET`
8. `GAOKAO_AI_BASE_URL`
9. `GAOKAO_AI_API_KEY`
10. `GAOKAO_AI_MODEL`

现在数据库不再走本机 MySQL，而是直接连 Supabase PostgreSQL。

如果你的 Lighthouse 是常见的 IPv4-only 环境，优先使用 Supabase 官方推荐的 `Shared Pooler / session mode` 连接串。

## 8. 先申请证书，再启服务

因为 Nginx 配置里默认会读取：

- `/etc/letsencrypt/live/${API_DOMAIN}/fullchain.pem`
- `/etc/letsencrypt/live/${API_DOMAIN}/privkey.pem`

所以建议先申请证书，再启动 Compose。

如果域名已经解析到服务器，可先执行：

```bash
sudo certbot certonly --standalone -d api.your-domain.com
```

把命令里的域名换成你真实的 `API_DOMAIN`。

## 9. 正式启动

证书和 `.env` 都准备好后：

```bash
cd /opt/gaokao-essay-backend/deploy/lighthouse
sudo docker compose up -d --build
```

这一步会同时拉起：

1. `backend`
2. `nginx`

其中：

- Supabase 里的表请先手动执行 `src/main/resources/schema-postgres.sql`
- 后端会以 `release` profile 启动
- Nginx 会把 `443` 的 HTTPS 请求转发到 `backend:8080`

## 10. 启动后先检查 4 件事

### 10.1 看容器状态

```bash
sudo docker compose ps
```

### 10.2 看后端日志

```bash
sudo docker compose logs -f backend
```

### 10.3 看健康接口

```bash
curl -s https://api.your-domain.com/api/health
```

目标至少满足：

1. `reviewReady=true`
2. `issuesCount=0`
3. `storageMode=postgres`
4. `generationAvailable=true`

### 10.4 看 Nginx 是否代理成功

浏览器直接访问：

```text
https://api.your-domain.com/api/health
```

## 11. 小程序这边还要补什么

### 11.1 改前端正式地址

文件：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-miniapp/utils/config.js`

把：

```js
apiBaseUrl: 'https://your-release-domain.com'
```

改成你的真实域名，例如：

```js
apiBaseUrl: 'https://api.your-domain.com'
```

### 11.2 微信后台配置服务器域名

你要在小程序后台配置：

1. `request` 合法域名
2. `uploadFile` 合法域名

通常都填：

- `https://api.your-domain.com`

## 12. 首版建议先关掉什么

为了降低首发复杂度，建议：

1. `GAOKAO_OCR_ENABLED=false`
2. `GAOKAO_PAYMENT_ENABLED=false`

等主链路稳定后再开：

- OCR
- 微信支付
- 包月 / 包年会员

## 13. 这条路线的优缺点

优点：

1. 成本比云托管低很多
2. 基本不用改现在的 Java 后端
3. 小程序前端也只需要改域名
4. 你已经写好的 JWT、会员、历史记录、知识库结构都能保留

缺点：

1. 你要自己管服务器
2. 要自己关注备份、磁盘、日志、证书续期
3. 没有云托管那种自动扩缩容体验

## 14. 当前最推荐的执行顺序

1. 买上海的 Lighthouse
2. 准备一个 API 子域名
3. 把域名解析到服务器
4. 上传 `gaokao-essay-backend`
5. 填 `deploy/lighthouse/.env`
6. 申请 HTTPS 证书
7. 运行 `docker compose up -d --build`
8. 用 `/api/health` 验活
9. 回头改小程序 `release.apiBaseUrl`
10. 再去微信后台补服务器域名白名单

## 15. 先别做的事

在主链路没跑通前，先不要急着做：

1. 微信支付
2. 包月 / 包年正式收费
3. OCR 正式开放
4. 多角色身份体系
5. 网页版同步上线

先把这一条链路跑通：

`小程序 -> HTTPS 域名 -> Nginx -> Spring Boot -> Supabase PostgreSQL -> 大模型`
