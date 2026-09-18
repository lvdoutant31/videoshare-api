# 开发与部署

## 工具链

使用 JDK 17 和仓库内 Maven Wrapper。父 POM 统一管理依赖版本、编译参数、测试插件和 Java 版本约束。子模块的 dependency 只声明所需组件和 scope，不自行写第三方版本。

Windows PowerShell：

```powershell
.\mvnw.cmd -v
Copy-Item .env.example .env
.\mvnw.cmd -B clean verify
java -jar .\yingliu-admin\target\yingliu-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Linux/macOS：

```bash
chmod +x mvnw
cp .env.example .env
./mvnw -B clean verify
java -jar yingliu-admin/target/yingliu-admin-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

运行前填写 `.env` 中 MySQL 和 Redis 配置。该工程没有复制原工程的本地密钥，也不会自动启动数据库。

## IDEA

导入根 `pom.xml`，将项目 SDK 与 Maven Runner JRE 都设置为 17。主类位于 `yingliu-admin/src/main/java/com/yingliu/YingliuApplication.java`。运行配置使用根目录作为 Working directory，active profile 选 local。

不要再从 `yingliu-api-app` 启动：它已变为普通接口库。两个接口库不需要分别运行。

## 测试

```powershell
# 单模块及其依赖
.\mvnw.cmd -B -pl yingliu-media -am test
# 全工程，包含配置与健康探针契约
.\mvnw.cmd -B verify
```

数据库集成测试通过 Testcontainers 使用 MySQL 8。Docker 29 使用测试资源 `yingliu-admin/src/test/resources/docker-java.properties` 中的 `api.version=1.44` 兼容旧版 Testcontainers；该资源只在测试 classpath 生效。运行工具必须有权限访问 Docker Desktop 管道。若 Docker 不可用，相关测试会标记为跳过；这不等同数据库链路通过。AppApiIntegrationTest 保留通过 TEST_MYSQL_URL、TEST_MYSQL_USER、TEST_MYSQL_PASSWORD 指定专用测试库的能力。测试会清理相关表，不应连接开发或生产数据库。

健康测试是 MVC 切片，覆盖路由、统一响应和 Trace ID；配置测试加载真实 YAML，验证 profile 与 OSS 装配。它们不替代完整数据库集成测试。

## 容器

```bash
docker build -f docker/app/Dockerfile -t ticktok-api .
docker run --rm -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod ticktok-api
```

容器内 localhost 是容器自身，数据库和 Redis 地址需指向容器可达的服务。生产环境由部署平台提供真实变量；若选择 OSS 模式，须按配置文档填全参数。本工程仅交付后端，不包含原前端和 Nginx 容器编排。
