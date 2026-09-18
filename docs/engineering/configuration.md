# 配置约定

所有运行 YAML 和 Flyway 脚本集中在 `yingliu-admin/src/main/resources`。库模块不再持有同名 application 配置，避免依赖 classpath 顺序覆盖。

| 文件 | 内容 |
| --- | --- |
| `application.yml` | 应用名、优雅关闭、JSON 时区、上传限制、会话、任务、监控、共享 OSS 环境变量 |
| `application-wecom.yml` | 企业微信自建应用授权、身份密钥和 state 配置；详见 [企业微信配置](wecom-authorization.md) |
| `application-local.yml` | 读取根目录 `.env`、本机 MySQL/Redis、调试开关、本地存储默认值 |
| `application-prod.yml` | 容器 MySQL/Redis、关闭鉴权绕过、OSS 默认值；不读取 `.env` |
| `application-test.yml` | 无外部服务的测试设置；不读取 `.env`，存储模式固定 local |

项目使用 Spring Boot 标准优先级：命令行参数可以覆盖运行环境；环境变量可以覆盖配置文件；profile 配置覆盖共享配置。local 导入的 `.env` 可提供占位符值。不要把 `.env` 导入机制理解为生产密钥来源。

## 环境变量

完整可复制清单见根目录 `.env.example`。

| 分组 | 变量 | 说明 |
| --- | --- | --- |
| 数据库 | `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD` | local 默认本机，prod 默认 mysql 容器；密码必须提供 |
| 缓存 | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` | local 默认本机，prod 默认 redis 容器 |
| 迁移 | `FLYWAY_ENABLED` | local/prod 默认 true；共享配置默认 false |
| HTTP | `APP_PORT` | 默认 8080 |
| 存储 | `MEDIA_STORAGE_TYPE`、`MEDIA_ROOT` | local 或 oss；文件位置相对于进程工作目录 |
| HTTP 上传 | `MEDIA_MAX_VIDEO_SIZE`、`MEDIA_MAX_REQUEST_SIZE` | 默认各 500MB，由 Spring multipart 处理 |
| 业务上传 | `MEDIA_MAX_VIDEO_SIZE_BYTES` | 默认 524288000 字节，由视频上传用例校验 |
| OSS 存储 | `OSS_ENDPOINT`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`、`OSS_CALLBACK_URL` | OSS 模式必填，由 `OssProperties` 校验；回调地址须可被 OSS 访问 |
| OSS 地址 | `OSS_URL_PREFIX` | 可选资源域名；为空时沿用存储实现的地址生成规则 |
| STS | `OSS_STS_ROLE_ARN`、`OSS_REGION`、`OSS_STS_ENDPOINT` | role ARN 仅调用旧 STS 签名/临时凭证上传接口时必填，业务 SDK 中转上传不需要；其他默认值见 YAML |
| 签名 | `OSS_UPLOAD_DIR`、`OSS_SIGNATURE_EXPIRE_SECONDS` | 默认 dir/ 和 3600 秒；有效期必须为正数 |

HTTP 文件限制、业务文件限制与 OSS 直传表单策略是三个实际存在的限制。本轮保留 OSS 表单原有上限 50,240,000 字节，未将其扩大为 500MB。

`OssProperties` 负责存储与回调参数，`OssUploadProperties` 负责 STS 与签名参数。控制器和上传工具不再自行读取 Environment。两个属性对象仅在 OSS 模式下绑定和校验，因此本地开发不需要配置云凭据。

## 开发与生产差异

local 保留原工程 `yingliu.admin.auth-disabled=true` 的开发默认值，可在 `.env` 关闭。prod YAML 固定为 false，原管理员绕过属性类还有生产环境保护。生产部署应按实际 HTTPS 环境设置 `YINGLIU_ADMIN_COOKIE_SECURE`。

local 的 Flyway `baseline-on-migrate=true` 和既有基线 `202608271200` 予以保留。prod 不自动设基线。迁移文件仅移动模块，文件名、版本和 SQL 内容不变；不得通过修改旧 SQL 修复已部署数据库。

## 与原工程的配置变化

- 本地示例改为 local 存储；原先写在 YAML 内的具体云账号、桶名和地址默认值移除，由环境变量提供。
- region、role ARN、STS endpoint 和签名有效期统一放入共享配置，生产环境可以使用同一份变量清单。
- `SignController` 和上传工具只在 OSS 模式下装配，修复 local/test 仍强制要求云参数的问题。
- 删除未使用的 JPA 建表配置；数据库仍由 Flyway 管理。
