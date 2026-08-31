# 映流短视频系统

映流是一个本地媒体存储的短视频管理与播放系统。当前仓库提供可运行的工程骨架，不含登录、上传、播放或业务数据实现。

## 工程结构

- `yingliu-api-app`：Spring Boot 启动模块与手机端健康接口。
- `yingliu-api-admin`：后台 API 模块与后台健康接口。
- `yingliu-common`：通用响应、错误码与链路追踪能力。
- `yingliu-domain`、`yingliu-media`、`yingliu-wecom`、`yingliu-worker`：预留领域、媒体、企微和异步任务模块。
- `yingliu-c`：Vue 3 + Vant 手机端工程。
- `yingliu-b`：Vue 3 + Element Plus 后台工程。
- `packages/shared`：共享前端类型与 HTTP 客户端。

详细设计见 [docs](docs/)。本地运行说明见 [docs/engineering/local-development.md](docs/engineering/local-development.md)。

## 健康检查

- `GET /health`
- `GET /api/v1/app/health`
- `GET /api/v1/admin/health`

以上端点目前不依赖 MySQL 或 Redis，可用于验证工程基础链路。
