# 后端 RuoYi 风格重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 明确后端模块职责、集中配置和依赖版本，并补充可维护的中文说明。

**Architecture:** 参考 RuoYi 的 common/framework/业务/启动分层，新增 yingliu-framework 和 yingliu-admin。保留现有 domain/service 业务划分及双端 HTTP 协议；媒体签名和上传逻辑集中到已有 OssUploadTool，由控制器调用。

**Tech Stack:** Java 17、Spring Boot 3.3.12、Maven 3.9.9、MyBatis-Plus、Sa-Token、MySQL、Redis。

## Global Constraints

- 不升级现有框架版本，不修改数据库迁移 SQL、接口路径、响应字段和鉴权语义。
- 主类保持 com.yingliu.YingliuApplication；Mapper 扫描保持 com.yingliu.service.**.mapper。
- 不读取或修改本地 .env；敏感配置仅使用环境变量占位符。
- 不创建多余业务接口；保留已有业务子包，基础配置统一归 framework。
- 独立工程交付到 D:/PrivateSoftware/gitClaude/ticktok-api，不修改原工程业务源码，不自动提交或发布。

## Task 1: Maven 分层和启动装配

**Files:** 父 pom.xml、全部子模块 pom.xml；新增 yingliu-framework/pom.xml、yingliu-admin/pom.xml；迁移 common/web、service/persistence、YingliuApplication、应用资源和启动集成测试；更新 docker/app/Dockerfile。

**Interfaces:** framework 提供统一异常处理、trace filter 和 MyBatis 装配；admin 聚合 api-app、api-admin、worker、wecom。common 仅提供响应、业务异常、加密工具，domain 仅依赖 MyBatis 注解。

- [x] 集中内部模块及独立第三方版本到 dependencyManagement，子模块只声明实际依赖。
- [x] 按以下依赖方向迁移文件并更新 Java import，保留 Bean 名称和扫描范围。

```text
yingliu-admin -> api-app + api-admin + framework + worker + wecom
api-app / api-admin -> framework + service
service -> common + domain + media
framework -> common
domain -> mybatis-plus-annotation
```

- [x] 执行 `./mvnw.cmd -B -DskipTests package`，验证全部源码及测试源码编译、仅启动模块生成 Boot JAR。

## Task 2: 媒体代码职责及配置

**Files:** yingliu-media/src/main/java/com/yingliu/media/{controller,config,storage}；媒体测试；yingliu-admin/src/main/resources/application*.yml。

**Interfaces:** `OssUploadTool.createPostSignature(String)` 返回原表单字段；`uploadFile(MultipartFile)` 返回 key/url/etag；SignController 保留原 GET/POST 路由；OssProperties 统一绑定 media.oss。

- [x] SignController 仅委托签名和上传，复用已有签名工具，修复基线中构造器与测试不一致的问题。
- [x] 将 SDK 上传实现从 SignController 移至 OssUploadTool，并用现有 MockMvc 测试验证返回值与 SDK 输入。
- [x] 统一 OSS 配置类、条件装配和环境变量；common YAML 维护共享默认项，local/prod 只保留环境差异，test 不加载外部服务。
- [x] 补充配置装配测试，验证 local 模式无需 OSS 凭据、OSS 缺参报错、prod/local/test 配置分层。

```powershell
./mvnw.cmd -B -pl yingliu-media -am test
```

## Task 3: 注释、文档与回归

**Files:** 新模块 package-info.java；关键框架、控制器、业务接口与领域模型的中文 Javadoc；README.md；docs/engineering/{backend-architecture,configuration,local-development}.md。

**Interfaces:** 文档说明模块依赖、启动命令、配置优先级、注释规范和测试要求。

- [x] 补全职责、事务/补偿、状态变化、单位及环境条件说明，避免给普通 getter/setter 添加重复注释。
- [x] 在架构说明中记录 RuoYi 对照关系、保留现有技术栈的原因和后续新增代码放置位置。
- [x] 执行 `./mvnw.cmd -B test` 和 `./mvnw.cmd -B -DskipTests package`，记录环境限制及原有失败，不把跳过测试当成通过。
- [x] 检查 diff 和跨模块引用，核实 Flyway 文件字节不变，旧入口不再产生可执行 JAR。

## 基线

- 原始 `mvn test` 在 yingliu-media:testCompile 失败：WebControllerTest 两处传入 OssUploadTool，但 SignController 构造器要求 Environment。
- 图索引排除了媒体包；该包采用定点文件读取补充探索。
