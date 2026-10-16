# 重构验证记录

验证日期：2026-09-06。交付目录：`D:/PrivateSoftware/gitClaude/ticktok-api`。

原工程：`D:/PrivateSoftware/gitClaude/shdy-tictok`，基线提交 `cc50dc58b1536f4b919284572cf1aa38c3f44d45`。原工程业务源码和本地 `.env` 未修改、未复制密钥。

## 已完成

- Maven `mvnw.cmd -B verify`：11 个 reactor 项目全部成功（父 POM + 10 个代码模块）。此前执行过 clean 构建；修复编译检查发现的问题后完成 verify。
- 测试汇总：49 个用例全部通过，0 个跳过，0 个失败，0 个错误（含 20 个 Docker/MySQL 集成用例）。
- 14 个媒体用例通过，覆盖签名表单、上传 SDK 输入、回调响应/验签、配置校验与 OSS/local 条件装配。
- 配置测试从真实 YAML 加载 prod/test 设置，验证共享 STS 参数、生产关闭绕过及测试不导入 `.env`。
- 健康 MVC 测试覆盖原三个探针路由、统一响应和 Trace ID。
- 构建产物：`yingliu-admin/target/yingliu-admin-0.1.0-SNAPSHOT.jar`，由该模块唯一执行 Spring Boot repackage。
- 7 个 Flyway SQL 文件的 SHA-256 与原工程逐一一致，文件名和集合也一致。
- 只读代码审查已核对依赖无环、主类/Mapper 扫描、自动配置入口、签名与上传兼容行为。审查发现的两处注释插入错误已修复，并通过后续编译。

## JAR 冒烟检查

使用实际 Boot JAR 启动 Tomcat，并请求以下地址，均返回 200、`code=OK`，且请求和响应的 `X-Trace-Id=refactor-smoke-001` 保持一致：

- `/health`
- `/api/v1/app/health`
- `/api/v1/admin/health`

本次冒烟采用 prod profile、local 媒体、随机端口、延迟初始化，关闭 Flyway 和 Redis 自动配置，提供测试占位数据库密码。目的是验证打包与真实 HTTP 探针装配；它不证明所有业务 Bean 已初始化或数据库业务可用。检查完成后已停止该进程。

## Docker/MySQL 完整应用测试

已在交付工程执行 `mvnw.cmd -B verify`，通过 Docker Desktop 4.80.0 / Engine 29.6.1 启动隔离的 MySQL 8.0 测试容器。4 组测试均加载完整 Spring 应用，各自从空数据库成功执行全部 7 份 Flyway 迁移。

| 测试类 | 通过 | 跳过 |
| --- | ---: | ---: |
| AdminContentIntegrationTest | 2 | 0 |
| AdminVideoIntegrationTest | 6 | 0 |
| AppApiIntegrationTest | 10 | 0 |
| AppVideoIntegrationTest | 2 | 0 |

覆盖分类/标签维护及校验、管理员创建免审核、人工审核、上下架和定时发布、手机端登录互动/评论/分享，以及公开视频可见性。测试没有连接或清理已有 dev-mysql 数据。

此前“Docker 不可用”的记录已纠正：Docker 一直在运行，实际障碍先是沙箱的命名管道权限，随后是 Testcontainers 1.19.8 的旧 Docker API 请求。数据库测试资源增加 `docker-java.properties` 的 `api.version=1.44` 后已成功连接；此设置不修改 Docker daemon，也不进入生产 JAR。

相关兼容背景：[Testcontainers 官方问题记录](https://github.com/testcontainers/testcontainers-java/issues/11211)。

### 本轮修复

- 恢复 `GlobalExceptionHandler.handleValidation` 的异常处理注解，参数校验失败按既有接口断言返回 422 / VALIDATION_FAILED，避免错误地返回 500。
- 视频测试使用 ObjectMapper 精确读取 data.id，避免正则匹配嵌套分类 ID 和中文解码产生的换行字符。
- 管理员创建断言跟随现有免审核规则，并验证 AUTO_APPROVED 审计记录；人工审核用独立 DRAFT 数据准备后调用真实提交、审核与发布接口。未修改管理员免审核业务实现。

### 尚未覆盖

本轮数据库测试使用本地媒体并排除 Redis 自动配置。真实 OSS、企业微信端到端联调及 Docker 应用镜像构建仍未执行；MySQL 集成验证已完成，真实本地 Redis 连接已在下述前后端联调中确认。

## 基线问题及处理

- 原媒体测试无法编译：测试传入 OssUploadTool，而 SignController 要求 Environment。现控制器委托已有上传工具，移除重复签名算法，保留原 HTTP 字段和上传 SDK 行为。
- 原 AppApiIntegrationTest 在静态初始化中无条件启动容器，无 Docker 时直接崩溃。现先判断 Docker 或 TEST_MYSQL_URL 是否可用，再启动容器；不可用明确跳过。
- 原健康测试用 test profile 启动整个业务上下文，但该 profile 禁用 Mapper，部分业务 Bean 仍要求 Mapper。现改为健康路由 MVC 切片；完整业务上下文仍需数据库环境。

## 交付范围

新增 framework 与独立启动模块，集中依赖版本和运行配置，整理媒体职责，将 VideoUploadService 从接口层迁入业务层，补充 58 处类型说明、9 份 package-info 以及事务/状态注释。保留原有业务包和技术栈，不宣称逐个重写了所有业务实现。

已记录的既存业务问题（例如管理员权限参数处理）未在结构重构中改变语义。后续功能调整需单独验证授权与业务契约。

## 2026-09-06 前后端实机联调

重构 JAR 已在 8080 启动（启动时 PID 135432，local profile），替换原工程同端口进程。为复用原本地 `.env` 和媒体文件，工作目录采用 `D:/PrivateSoftware/gitClaude/shdy-tictok`，JAR 采用交付目录的 `yingliu-admin/target/yingliu-admin-0.1.0-SNAPSHOT.jar`。关闭本地种子初始化，避免重置现有开发数据。运行日志位于原工程 `tmp/ticktok-api-runtime.log`。

| 范围 | 验证结果 |
| --- | --- |
| 全量构建和 Docker 测试 | `mvnw.cmd -B verify` 成功，49 通过、0 失败、0 跳过 |
| MySQL / Redis | 完整应用启动成功，Redis 连接池建立；Actuator health 为 UP |
| 管理端代理 | 通过 `http://localhost:3000/api/...` 检查健康、会话、工作台四接口、视频列表/详情、分类、标签、用户、评论、举报和操作日志，共 14 个 GET 均 200 / OK |
| 参数校验 | 通过前端代理提交空名称/负热度标签，422 / VALIDATION_FAILED |
| 标签页面 | 创建 `联调验证_20260906_1008`、按关键字搜索、热度从 0 改为 1、删除后无匹配记录；测试标签 ID 2096420132682809345 已软删除，审计记录保留 |
| 视频页面 | 搜索“客户现场安全操作要点”，只返回 ID 9201；详情文本加载正常，预览有下述前端适配问题 |
| 手机端媒体 | 5174 的 `/media/sintel-trailer.mp4` Range 请求返回 206 / 1024 bytes；浏览器 video.readyState=4、paused=false、currentTime>7 秒，截图确认实际播放 |

联调修复了两个本地环境问题：将 `D:/PrivateSoftware/gitClaude/yingliu-c/.env` 的 VITE_PROXY_TARGET 从 8081 改为 8080（其他项保留），并从该前端的 `public/media` 补齐已有 3 个示例 MP4 到原工程 `document/media`。手机端重载后进程退出，已按同工程、同端口 5174 恢复（启动时 PID 137600）。没有修改前端业务源码。

管理端视频详情预览尚未通过：后端返回 originalMedia.url=/media/mov_bbb.mp4，但 `D:/PrivateSoftware/gitClaude/yingliu-b/src/features/video/repository.ts:45` 只映射 previewUrl，页面显示“预览暂不可用”。需要前端适配 originalMedia.url，并为本地媒体配置 /media 代理；本轮未修改其业务源码。

本次仅验证本地媒体模式。真实 OSS 上传、真实企业微信登录未执行；视频上传、审核和发布状态流程由 Docker 集成测试覆盖。本地 auth-disabled 调试会话不代表真实登录权限验证通过。

运行日志记录过浏览器取消视频分段连接的 ClientAbortException；通用异常处理器随后尝试向 video/mp4 响应写 JSON，产生一条转换警告。复测分段请求和实际播放成功，但断连日志处理仍可单独完善。手机端示例头像/封面有缺图，未纳入本轮后端修复。

## 后续：后端中转上传方案确认与预览修复

用户已选择“后端接收 file/字段，由 SDK 自动签名上传 OSS”。业务链路沿用 VideoUploadService -> OssStorageService -> 验签回调 -> 事务入库 -> 返回 originalMedia.url。新增回调失败不启动入库事务的测试；SDK 上传不再强制要求旧 STS 接口的 Role ARN，旧接口仍在调用时检查该项。

之前记录的管理端“预览暂不可用”已修复：yingliu-b 的 repository.ts 增加 originalMedia.url 回退映射，vite.config.ts 增加 /media 代理。构建和浏览器播放检查通过。

本轮 Maven test 全量结果为 51 通过、0 失败、0 跳过（含原 20 个 Docker/MySQL 用例）；随后跳过重复测试重新打包。前端构建成功，存在既有的大 chunk 提示。后端当前仍以 local 存储运行，真实 OSS 云端联调等待 Bucket、Endpoint、回调域名及媒体域名配置。详见 [视频上传流程](video-upload.md)。
