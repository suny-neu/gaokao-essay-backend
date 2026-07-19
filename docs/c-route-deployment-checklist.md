# C 路线部署清单

这份清单对应当前项目确定的 `C` 路线：

- 微信小程序前端
- 云托管 Spring Boot 后端
- 托管 MySQL
- 外部大模型 / OCR 服务

它不是一份抽象方案，而是按当前仓库实际情况整理的上线执行清单。

当前仓库基线：

- 后端目录：`/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend`
- 小程序目录：`/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-miniapp`
- 后端已具备 `release` 启动脚本：`scripts/start-release.sh`
- 后端已具备正式环境变量模板：`.env.release.example`
- 后端已补 `Dockerfile` 与 `.dockerignore`
- 小程序已接好 `wx.login`、分块流式请求、`wx.uploadFile`、上线检查页

重要判断：

- 当前小程序代码走的是 `wx.request` / `wx.uploadFile` 这条标准 HTTPS 链路
- 这意味着当前正式上线方案默认仍需要真实 HTTPS 域名、ICP备案、服务器域名白名单
- 只有未来把前端调用方式改成微信云托管私有协议，例如 `callContainer` / `connectContainer`，才可以进一步弱化域名配置步骤

## 1. 先决条件

上线前先准备齐这 6 项：

1. 真实小程序 `AppID`
2. 小程序管理员账号和开发者权限
3. 云托管环境
4. 托管 MySQL 实例
5. 备案通过的业务域名
6. 可用的 HTTPS 证书

当前不建议：

- 继续用 `touristappid`
- 生产环境继续连本地 MySQL
- 直接把 `AppSecret`、模型密钥放进小程序前端

## 2. 你这条路线到底怎么落

推荐的正式形态是：

1. 小程序继续保留现有原生实现
2. 后端继续保留现有 Spring Boot 代码
3. 后端打成容器，部署到云托管
4. 数据库存到托管 MySQL
5. 小程序继续通过真实 HTTPS 域名访问后端
6. 微信上线前完成合法域名、隐私指引、提审资料配置

这样做的原因：

- 不推翻你已经写好的 Java 后端
- 比自己买云服务器、省心很多
- 后面补网页端时，后端还能直接复用
- 更适合承接 JWT、会员、OCR、历史记录、弱项画像和 Agent 成长链路

## 3. 第 0 步：账号与云资源开通

先把资源位开出来，再谈部署。

你需要开通：

1. 微信小程序正式账号
2. 云托管环境
3. 托管 MySQL
4. 对象存储或云存储
5. 域名和证书

建议资源选择：

- 云托管和 MySQL 放同一云厂商、同一地域
- MySQL 与后端尽量同地域、同 VPC
- 域名优先单独给 API 用一个子域名，例如 `api.xxx.com`

如果你暂时不做支付：

- 先不要开微信支付
- 首版先把会员能力保留在后端模型里，但支付开关继续关闭

## 4. 第 1 步：补齐仓库里还缺的上线材料

### 4.1 必补项

当前仓库里正式上云前至少还要补这 3 件事：

1. 正式构建 / 镜像发布说明
2. 云托管环境变量录入清单
3. 生产数据库初始化脚本执行记录

### 4.2 Dockerfile 要求

当前仓库已经补了 `Dockerfile`，可以直接作为云托管镜像构建起点。

推荐做法：

- 使用多阶段构建
- 构建阶段用 Maven Wrapper 打包
- 运行阶段只保留 JRE 和 jar 包
- 容器内以 `release` profile 启动

推荐启动目标：

- 暴露端口 `8080`
- 启动命令使用 `java -jar app.jar --spring.profiles.active=release`

不建议正式环境继续使用：

- `mvn spring-boot:run` 直接当生产启动方式

这个命令适合本地验证，不适合长期线上运行。

## 5. 第 2 步：准备生产数据库

### 5.1 建库

当前项目生产库标准名已经明确：

- 数据库名：`gaokao_essay`

建议：

- 单独创建生产账号，不用 `root`
- 只给这个账号当前库的最小必要权限

### 5.2 导入结构

当前仓库已有 SQL：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/mysql-upgrade-20260616.sql`

正式导入顺序建议：

1. 创建 MySQL 实例
2. 创建数据库 `gaokao_essay`
3. 创建业务账号
4. 执行 `mysql-upgrade-20260616.sql`
5. 启动后端
6. 调 `/api/health` 检查是否识别为 `mysql`

### 5.3 启动后确认

健康检查至少确认这 4 项：

1. `mysqlEnabled=true`
2. `capabilities.storageMode=mysql`
3. `reviewReady=true`
4. `issuesCount=0`

如果 `issuesCount` 不为 `0`：

- 先不要提审
- 先把健康检查页上的风险项清完

## 6. 第 3 步：整理正式环境变量

### 6.1 以当前项目为准的必填项

直接基于这个文件整理正式配置：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/.env.release.example`

至少要填：

- `GAOKAO_AUTH_TOKEN_SECRET`
- `GAOKAO_MYSQL_URL`
- `GAOKAO_MYSQL_USERNAME`
- `GAOKAO_MYSQL_PASSWORD`
- `WECHAT_APP_ID`
- `WECHAT_APP_SECRET`
- `GAOKAO_AI_BASE_URL`
- `GAOKAO_AI_API_KEY`
- `GAOKAO_AI_MODEL`

如果首版开 OCR：

- `GAOKAO_OCR_ENABLED=true`
- `GAOKAO_OCR_BASE_URL`
- `GAOKAO_OCR_API_KEY`
- `GAOKAO_OCR_MODEL`

如果首版不开 OCR：

- 保持 `GAOKAO_OCR_ENABLED=false`
- 前端拍照识题 / 识材 / 识文入口应同步隐藏

如果首版不开支付：

- 保持 `GAOKAO_PAYMENT_ENABLED=false`

### 6.2 正式环境必须关掉的调试项

这几个值必须保持正式态：

- `GAOKAO_LOCAL_AUTH_FALLBACK_ENABLED=false`
- `GAOKAO_REQUEST_OPENID_FALLBACK_ENABLED=false`
- `GAOKAO_BILLING_DEBUG_ENABLED=false`
- `GAOKAO_STRICT_STARTUP_CHECKS=true`
- `GAOKAO_MSG_SEC_ENABLED=true`

### 6.3 密钥放置规则

必须只放服务端：

- `WECHAT_APP_SECRET`
- 大模型 API Key
- OCR API Key
- JWT Secret
- 支付密钥和证书

绝对不要放进：

- 小程序前端配置
- Git 仓库
- 公开截图

## 7. 第 4 步：部署后端到云托管

### 7.1 云托管服务配置建议

服务创建时至少明确这几项：

1. 地域
2. CPU / 内存
3. 容器端口 `8080`
4. 环境变量
5. 访问方式
6. 日志与监控

建议首版保守配置：

- 单实例起步
- 打开自动重启
- 打开基础日志
- 准备好后续扩容能力

### 7.2 访问方式

当前项目建议：

- 云托管暴露一个真实 HTTPS 访问域名
- 小程序继续通过 `https://你的域名/api/...` 访问

原因：

- 当前前端代码已经按 `wx.request` + `authEndpoint` + `healthEndpoint` 这套模式写好
- 这样改动最小

可选优化：

- 如果后续你愿意改前端网络层，可以再切到微信云托管私有调用协议
- 那时可以减少一部分域名配置工作

### 7.3 后端上线后首个验证

部署完成后先用健康接口验活：

```bash
curl -s https://你的后端域名/api/health
```

至少确认：

- 服务能通
- 容器已载入正式环境变量
- MySQL 已连通
- `reviewReady=true`

## 8. 第 5 步：域名、备案、证书

这是小程序最容易卡住的一步。

### 8.1 当前路线下必须满足

由于当前代码走标准 HTTPS 请求链路，所以你需要：

1. 真实域名
2. ICP 备案
3. 有效 HTTPS 证书
4. 小程序后台服务器域名配置

### 8.2 域名配置注意点

根据微信官方网络文档，当前链路下要特别注意：

- 只允许请求已配置的服务器域名
- `request`、`uploadFile`、`downloadFile` 走 `https`
- WebSocket 走 `wss`
- 不能使用 `IP` 或 `localhost`
- 域名需要ICP备案
- 证书要有效、信任链完整、域名匹配
- TLS 至少支持 `1.2`

### 8.3 当前项目要配的白名单

小程序后台至少配置：

1. `request` 合法域名
2. `uploadFile` 合法域名

如果后续真开 WebSocket，再补：

3. `socket` 合法域名

## 9. 第 6 步：把小程序切到正式配置

### 9.1 先换掉测试身份

你现在正式上线路线里，第一件事就是：

- 不再使用 `touristappid`
- 改成真实 `AppID`

### 9.2 前端配置必须切的项

你要把小程序 `release` 档配置切到真实值，至少包括：

1. `apiBaseUrl`
2. `serviceMode`
3. `billingMode`

说明：

- 当前项目已经改成按微信环境自动判定档位
- `develop` 会走 `local`
- `trial` 和 `release` 会自动走 `release`
- 正式上线时不需要再手动改 `activeProfile`

正式目标应满足：

- 指向真实 HTTPS 域名
- 不再指向占位域名
- 不再依赖 mock
- 不再依赖本地调试 fallback

### 9.3 当前项目已有的上线检查页

当前小程序已经有一页专门做上线检查：

- 文件：`/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-miniapp/pages/checklist/index.js`

这页会帮助你检查：

- 是否还在 `touristappid`
- 是否已切到正式配置
- `/api/health` 是否连通
- 隐私授权能力是否正常
- OCR 开关状态是否与后端一致

建议提审前至少完整跑一遍。

## 10. 第 7 步：微信登录正式联调

当前项目登录链路是：

1. 小程序 `wx.login`
2. 后端拿 `code`
3. 服务端调用 `code2session`
4. 后端建立 `OpenID -> user_id`
5. 后端签发 JWT

你提审前必须真机验证这 5 件事：

1. `wx.login` 能拿到 `code`
2. 后端 `WECHAT_APP_ID / WECHAT_APP_SECRET` 已填真实值
3. `code2session` 能返回真实身份
4. JWT 能落到本地存储
5. 后续请求都能带上登录态

只要这里没通：

- 陪练、批改、试用次数、历史记录都会是假联通

## 11. 第 8 步：内容安全与隐私合规

### 11.1 内容安全

当前产品涉及：

- 用户文本输入
- 模型输出
- OCR 识别结果

所以正式环境里建议按强制项处理：

1. 输入前置检查
2. 输出后置检查
3. OCR 文本纳入同一安全链路

如果安全接口没真开：

- 不要提审

### 11.2 隐私合规

当前产品至少会涉及：

- 登录态
- 拍照 / 相册
- OCR 文本上传

所以你需要：

1. 在后台填写《小程序用户隐私保护指引》
2. 声明拍照 / 相册 / 上传等实际用途
3. 在真机上验证隐私授权弹窗链路

如果你声明了“未采集隐私”，但代码里又实际调用了相关接口：

- 提审会非常危险

## 12. 第 9 步：真机联调清单

正式提审前，至少按顺序完成这 10 次真机检查：

1. 首页能打开
2. 登录成功
3. `/api/health` 通过
4. 作文陪练可返回真实结果
5. 严格批改可返回真实结果
6. 历史记录能写入并再次读出
7. 总量试用 5 次逻辑生效
8. OCR 若开启，可正常识别并回填
9. 隐私授权链路正常
10. 异常时页面有提示，不白屏

建议你至少测试 3 类账号：

1. 新用户
2. 已消耗过试用次数的用户
3. 管理员 / 开发者自己

## 13. 第 10 步：提审流程

按微信官方发布流程，当前建议顺序：

1. 开发者工具预览
2. 上传代码
3. 设置体验版
4. 体验成员真机回归
5. 提交审核
6. 审核通过后再发布

如果首版功能较多，建议：

- 第一版尽量功能收敛
- 先全量最小闭环通过审核
- 后续再小步迭代

## 14. 第 11 步：首版建议延后项

为了提高首版成功率，这 4 件事建议能延后就先延后：

1. 微信支付正式接入
2. 包月 / 包年完整购买闭环
3. 复杂分享裂变
4. UGC 社区化内容

当前最稳妥的首版目标是：

- 陪练
- 批改
- OCR
- 历史记录
- 总量试用 5 次
- 个人提分档案基础版

## 15. 第 12 步：上线后运维

上线后不要只盯前端页面，要同时盯这 6 类信号：

1. 云托管实例状态
2. `/api/health`
3. MySQL 连接与慢查询
4. 模型调用失败率
5. OCR 失败率
6. 用户试用次数扣减是否准确

至少准备：

- 日志查看入口
- 数据库备份策略
- 紧急回滚预案
- OCR / AI 服务商故障时的降级提示

## 16. 以当前项目为准的 Go / No-Go 标准

### 16.1 可以提审

满足以下条件才建议提审：

1. 真实 `AppID` 已替换完成
2. 真实 HTTPS 域名已接通
3. 小程序后台已配合法域名
4. `/api/health` 返回 `reviewReady=true`
5. `issuesCount=0`
6. 微信登录真联通
7. 隐私保护指引已填写
8. 内容安全已真开
9. 非正式调试开关全部关闭
10. 真机完整走通过至少一轮

### 16.2 先别提审

只要出现以下任一情况，就先别提审：

1. 还在用 `touristappid`
2. `apiBaseUrl` 还是占位域名
3. MySQL 还连本地
4. 健康检查还有告警
5. `AppSecret` 还没填真实值
6. 内容安全未联通
7. 隐私声明没填
8. OCR 或生成入口仍连着半成品链路

## 17. 当前最推荐的执行顺序

如果你现在就准备往正式上线推进，建议照这个顺序做：

1. 补 `Dockerfile`
2. 开云托管和托管 MySQL
3. 导入生产数据库
4. 把后端部署到云托管
5. 配真实域名、备案、证书
6. 小程序切真实 `AppID` 和正式域名
7. 真机打通登录、陪练、批改、OCR、历史记录
8. 清空 `/api/health` 的所有告警
9. 填隐私保护指引和审核资料
10. 上传、体验、提审、发布

## 18. 参考

官方文档：

- 微信小程序网络要求：[网络 | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)
- 微信小程序发布流程：[小程序协同工作和发布 | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/framework/quickstart/release.html)
- 微信小程序隐私授权：[小程序隐私协议开发指南 | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/framework/user-privacy/PrivacyAuthorize.html)
- 微信内容安全接口：[msgSecCheck | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/security/sec-check/msgSecCheck.html)
- CloudBase 文档首页：[CloudBase 官方文档](https://docs.cloudbase.net/)
- 腾讯云 MySQL 托管产品：[TencentDB for MySQL](https://cloud.tencent.com/product/cdb)

仓库内说明：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/gaokao-essay-agent-requirements.md`
- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/release-start.md`
- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/.env.release.example`
