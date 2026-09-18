# 映流后端 · ticktok-api

独立的 Java 后端工程，支持手机端、管理端、视频媒体与定时发布。结构参考 [RuoYi 官方模块说明](https://doc.ruoyi.vip/ruoyi/document/xmjs.html)，保留 Java 17、Spring Boot 3、MyBatis-Plus 和 Sa-Token。

## 工程结构

```text
ticktok-api
├── pom.xml                 # 模块聚合、依赖版本和构建插件
├── yingliu-admin           # 唯一启动入口、配置、Flyway、整应用测试
├── yingliu-framework       # 请求追踪、异常响应、MyBatis 装配
├── yingliu-api-admin       # 管理端接口和会话适配
├── yingliu-api-app         # 手机端接口和会话适配
├── yingliu-service         # 业务用例、事务、Mapper
├── yingliu-domain          # 实体与 MyBatis 映射注解
├── yingliu-common          # 通用响应、异常、加密工具
├── yingliu-media           # controller / config / storage
├── yingliu-wecom           # 企业微信 SDK 封装与授权配置
└── yingliu-worker          # 定时发布任务
```

`yingliu-admin` 同时运行手机端和管理端接口；两个接口模块都是普通 JAR。前端继续使用原工程，通过 HTTP 访问该后端。

## 本地运行

需要 JDK 17、MySQL 8 和 Redis 7。Maven 3.9.9 由仓库 Wrapper 提供。

```powershell
# 在 ticktok-api 根目录执行，填写数据库信息；默认使用本地媒体存储。
Copy-Item .env.example .env
.\mvnw.cmd -B clean verify
java -jar .\yingliu-admin\target\yingliu-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

IDEA 导入根 `pom.xml`，选择 JDK 17，运行 `yingliu-admin` 下的 `com.yingliu.YingliuApplication`，Working directory 设置为工程根目录，以便 local profile 读取 `.env`。

健康端点：`/health`、`/api/v1/app/health`、`/api/v1/admin/health`。默认端口 8080。

## 配置与测试

- 配置唯一入口：`yingliu-admin/src/main/resources/application.yml`，环境差异位于 `application-local.yml`、`application-prod.yml`、`application-test.yml`。
- 企业微信配置集中在 `application-wecom.yml`，接入步骤见 [企业微信授权说明](docs/engineering/wecom-authorization.md)。
- `.env.example` 是配置清单；只有 local profile 自动加载根目录 `.env`。生产配置从运行环境注入。
- OSS 模式须填写存储、回调和 STS 配置；local 模式不创建 OSS 签名入口。
- `mvnw.cmd test` 执行单元与契约测试。数据库集成测试需要 Docker；环境不可用时会显式跳过。App 核心链路测试也可通过 `TEST_MYSQL_URL`、`TEST_MYSQL_USER`、`TEST_MYSQL_PASSWORD` 指向专用测试库。
- 数据库测试会清理测试数据，测试库必须独立于开发和生产库。

进一步阅读：[架构与代码放置约定](docs/engineering/backend-architecture.md)、[配置说明](docs/engineering/configuration.md)、[开发与部署](docs/engineering/local-development.md)、[重构验证记录](docs/engineering/refactor-verification.md)、[接口路径约定](docs/engineering/api-path-conventions.md)。

## 本地视频上传与播放

上传接口 `POST /api/v1/admin/videos/upload`（multipart，带文件创建；JSON 创建草稿为 `POST /api/v1/admin/videos`）在 local 模式下将文件写入 `MEDIA_ROOT`，完整 URL 写入数据库。执行 `docker compose -f compose.media.yml up -d` 启动 Nginx，前端直接播放返回的地址。配置和验证步骤见 [本地视频存储](docs/engineering/local-video-storage.md)。
