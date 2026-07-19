# Release 启动说明

这份后端现在已经支持正式 `release` 启动，但上线前你需要把真实环境变量补齐。

如果你要按正式微信小程序链路完整上线，而不只是本地启动后端，请同时看：

- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/c-route-deployment-checklist.md`
- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/lighthouse-deployment-checklist.md`
- `/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/supabase-postgres-setup.md`

## 你要填的文件

先复制：

```bash
cp /Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/.env.release.example /Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/.env.release.local
```

然后把 `.env.release.local` 里的这些值补成真实配置：

- `GAOKAO_AUTH_TOKEN_SECRET`
- `GAOKAO_DATABASE_URL`
- `GAOKAO_DATABASE_USERNAME`
- `GAOKAO_DATABASE_PASSWORD`
- `WECHAT_APP_ID`
- `WECHAT_APP_SECRET`
- `GAOKAO_AI_BASE_URL`
- `GAOKAO_AI_API_KEY`
- `GAOKAO_AI_MODEL`

如果你想同时限制“单日免费次数”，再补：

- `GAOKAO_TRIAL_DAILY_LIMIT`

如果你要开 OCR，就继续补：

- `GAOKAO_OCR_BASE_URL`
- `GAOKAO_OCR_API_KEY`
- `GAOKAO_OCR_MODEL`

如果你要开微信支付，再补：

- `GAOKAO_PAYMENT_ENABLED=true`
- `GAOKAO_PAYMENT_NOTIFY_URL`
- `WECHAT_PAY_MERCHANT_ID`
- `WECHAT_PAY_MERCHANT_SERIAL_NUMBER`
- `WECHAT_PAY_PRIVATE_KEY_FILE`
- `WECHAT_PAY_PLATFORM_PUBLIC_KEY_FILE`
- `WECHAT_PAY_PLATFORM_SERIAL_NUMBER`
- `WECHAT_PAY_API_V3_KEY`

## 启动命令

```bash
/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/scripts/start-release.sh
```

如果你想指定别的 env 文件：

```bash
/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/scripts/start-release.sh /path/to/your.env
```

## 启动后检查

先看健康接口：

```bash
curl -s http://127.0.0.1:8080/api/health
```

重点确认：

- `databaseEnabled` 是 `true`
- `databaseKind` 是 `postgres`
- `capabilities.storageMode` 是 `postgres`
- `issuesCount` 必须为 `0`

如果你看到 `state-file`，说明这次启动还不能算正式放行。

## 目前这套库已经确认好的点

- PostgreSQL / Supabase 可直接使用 `schema-postgres.sql`
- `coach_template` 已自动 seed 20 条模板

## 上线前一定要关掉的调试项

这些值必须是正式态：

- `GAOKAO_LOCAL_AUTH_FALLBACK_ENABLED=false`
- `GAOKAO_REQUEST_OPENID_FALLBACK_ENABLED=false`
- `GAOKAO_BILLING_DEBUG_ENABLED=false`
- `GAOKAO_STRICT_STARTUP_CHECKS=true`
- `GAOKAO_MSG_SEC_ENABLED=true`

如果健康接口里还出现这几项告警，先别提审。
