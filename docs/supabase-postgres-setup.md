# Supabase PostgreSQL 接入说明

这份说明对应当前项目把结构化存储切到 Supabase PostgreSQL 的版本。

适用场景：

- 小程序前端继续不变
- Spring Boot 后端继续不变
- 数据库不再用本机 MySQL 或腾讯云 MySQL
- 改用 Supabase 托管 PostgreSQL

## 1. 当前代码已经改到哪里

当前后端已经完成这些调整：

1. 增加了 PostgreSQL JDBC 驱动
2. 环境变量改为优先读取 `GAOKAO_DATABASE_*`
3. JDBC repository 去掉了 MySQL 专属 `ON DUPLICATE KEY UPDATE`
4. 新增 PostgreSQL 建表文件：
   - `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/src/main/resources/schema-postgres.sql`

兼容性说明：

- 旧的 `GAOKAO_MYSQL_*` 变量现在仍然能作为 fallback 读取
- 但正式接 Supabase 时，应该统一改填 `GAOKAO_DATABASE_*`

## 2. 在 Supabase 控制台拿什么

你主要需要 3 个值：

1. 连接串主机 / 端口
2. 数据库用户名
3. 数据库密码

Supabase 官方文档说明：

- 长连接后端优先用 direct connection
- 如果你的运行环境是 IPv4-only，则推荐用 Shared Pooler 的 session mode

来源：

- [Supabase 连接 Postgres 官方文档](https://supabase.com/docs/guides/database/connecting-to-postgres)

## 3. 你这套项目更推荐哪种连接方式

如果你的后端跑在：

- Lighthouse
- 普通 Linux 服务器
- Docker 容器

优先判断服务器是否能走 IPv6：

1. 如果能稳定走 IPv6：
   - 可以用 direct connection
2. 如果是常见的 IPv4-only 服务器：
   - 用 Shared Pooler 的 session mode

不建议你这套 Spring Boot + JDBC 后端首版就用：

- transaction mode

因为 Supabase 官方明确说明 transaction mode 更适合 serverless / edge，并且不支持 prepared statements。

## 4. 连接串怎么写

### 4.1 IPv4-only 场景，推荐 session mode

Supabase 控制台点 `Connect` 后，拿 session mode 连接信息。

JDBC 写法类似：

```text
jdbc:postgresql://aws-REGION.pooler.supabase.com:5432/postgres?sslmode=require
```

用户名通常不是纯 `postgres`，而是 Supabase 给你的那串带项目前缀的用户名。

### 4.2 如果你的服务器能用 IPv6，可用 direct connection

JDBC 写法类似：

```text
jdbc:postgresql://db.PROJECT_REF.supabase.co:5432/postgres?sslmode=require
```

## 5. 当前项目应填哪些环境变量

至少要填：

```text
GAOKAO_DATABASE_ENABLED=true
GAOKAO_DATABASE_URL=jdbc:postgresql://...
GAOKAO_DATABASE_USERNAME=...
GAOKAO_DATABASE_PASSWORD=...
GAOKAO_DATABASE_DRIVER_CLASS_NAME=org.postgresql.Driver
```

另外这些仍然是必填：

```text
GAOKAO_AUTH_TOKEN_SECRET
WECHAT_APP_ID
WECHAT_APP_SECRET
GAOKAO_AI_BASE_URL
GAOKAO_AI_API_KEY
GAOKAO_AI_MODEL
```

## 6. 怎么把表建进去

当前项目已经提供 PostgreSQL 版全量建表文件：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/src/main/resources/schema-postgres.sql`

你可以在 Supabase 的 SQL Editor 里直接执行这份 SQL。

如果你之前已经建过表，再升级到当前版本时，先执行这个增量脚本：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/postgres-upgrade-20260625.sql`

这个脚本会补上 `client_request_id`，用于拦截重复提交，避免重复扣试用次数和重复写历史记录。

建完后再启动后端。

## 7. 启动后看什么

先看健康接口：

```bash
curl -s http://127.0.0.1:8080/api/health
```

目标至少满足：

1. `databaseEnabled=true`
2. `databaseKind=postgres`
3. `capabilities.storageMode=postgres`
4. `reviewReady=true`
5. `issuesCount=0`

## 8. 当前不建议你一起改的东西

先不要同时做这些：

1. 改成 Supabase Auth
2. 让小程序前端直连 Supabase
3. 把 Storage 也切到 Supabase

当前最稳的结构仍然是：

`微信小程序 -> Spring Boot 后端 -> Supabase PostgreSQL`
