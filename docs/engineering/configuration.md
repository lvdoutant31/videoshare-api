# 配置与迁移基线

- 本地配置来自 `.env` 与 Spring Profile；生产环境仅从受控运行环境注入密钥。
- 禁止在 YAML、前端环境文件、日志或数据库迁移中写入真实密码、企微密钥、HMAC 密钥和令牌。
- 当前工程不启用数据库自动建表，首个数据模型实现时使用 Flyway。
- Flyway 文件放入 `yingliu-api-app/src/main/resources/db/migration/`，命名为 `VyyyyMMddHHmm__description.sql`。
- 媒体目录仅作为未来 Nginx 内部重定向目标，当前没有任何公开文件读取路由。
