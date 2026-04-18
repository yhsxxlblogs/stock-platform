# 数据库迁移文件

## 命名规范

迁移文件命名格式：`YYYYMMDD_HHMMSS_description.sql`

示例：
- `20260118_120000_add_user_email_index.sql`
- `20260118_130000_create_stock_logs_table.sql`

## 迁移文件示例

```sql
-- 20260118_120000_add_user_email_index.sql
-- 为用户表添加邮箱索引

ALTER TABLE users ADD INDEX idx_email (email);
```

## 自动迁移

迁移文件会在容器启动时自动执行，执行记录保存在 `schema_migrations` 表中。

## 手动执行迁移

```bash
./scripts/auto-migrate.sh
```
