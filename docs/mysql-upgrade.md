# MySQL 升级说明

这次升级主要是把后端数据库补到“正式结构化陪练”这一版，核心变化有两块：

- `essay_record` 新增 `coach_plan_json`
- 新增 `coach_template` 表，用来存结构化陪练模板

支付相关的 `payment_order` 也一并补进正式库，避免后面再单独迁一次。

## 文件位置

- 升级脚本：[mysql-upgrade-20260616.sql](/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/mysql-upgrade-20260616.sql)
- 当前全量建表定义：[schema-mysql.sql](/Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/src/main/resources/schema-mysql.sql)

## 推荐执行顺序

1. 先备份正式数据库。
2. 用 MySQL 客户端连上你的正式库。
3. 执行 `USE 你的数据库名;`
4. 跑 `mysql-upgrade-20260616.sql`
5. 重启后端，并确保 `GAOKAO_MYSQL_ENABLED=true`
6. 看后端日志，确认 `coach_template` 已自动 seed

## 执行示例

```bash
mysql -h 你的主机 -P 3306 -u 你的用户名 -p 你的数据库名 < /Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/mysql-upgrade-20260616.sql
```

如果你是先登录 MySQL 再手动执行：

```sql
USE your_database_name;
SOURCE /Users/jianglei4899126.com/Documents/Codex/gaokao-essay-backend/docs/mysql-upgrade-20260616.sql;
```

## 跑完后检查什么

重点看这 4 件事：

1. `essay_record` 里已经有 `coach_plan_json`
2. `coach_template` 表已经创建成功
3. 重启后 `coach_template` 不是空表
4. 小程序做一次“作文陪练”，历史记录里能拿到 `coachPlan`

## 说明

- 这份脚本按“可重复执行”来写，二次执行不会重复加同名列和索引。
- 如果你的数据库版本太旧，执行时遇到 `information_schema` 或索引语法兼容问题，再把报错贴给我，我继续帮你收口。
