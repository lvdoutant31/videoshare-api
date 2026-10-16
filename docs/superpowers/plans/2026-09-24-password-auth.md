# C 端密码认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 C 端认证收敛为手机号密码注册与登录，忘记密码改为联系管理员，并暂时隐藏短信验证码与企业微信入口。

**Architecture:** `sys_user` 增加可空 BCrypt 密码散列字段，AppAuthService 提供密码注册和登录能力，API 层签发与现有登录一致的 Sa-Token 会话。前端保留既有响应式视觉，只简化认证状态和表单；企业微信后端实现保留，备案后可重新开放入口。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、Flyway、jBCrypt、Vue 3、Pinia、Vitest、Playwright

## Global Constraints

- 暂不使用手机验证码登录。
- 企业微信登录暂不启用，域名备案后再开启。
- 忘记密码不提供匿名自助重置，只提示联系管理员。
- 密码只存储 BCrypt 散列，接口和日志不得返回或记录明文密码。
- 现有企业微信账号允许保留空密码，但不能通过密码登录。

---

### Task 1: 用户密码存储与领域服务

**Files:**
- Create: `yingliu-admin/src/main/resources/db/migration/V202609241000__add_app_user_password.sql`
- Modify: `yingliu-domain/src/main/java/com/yingliu/domain/user/AppUser.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/AppAuthService.java`
- Modify: `yingliu-service/src/main/java/com/yingliu/service/app/DefaultAppAuthService.java`

**Interfaces:**
- Produces: `registerByPassword(phone, password, agreementVersion)` 和 `loginByPassword(phone, password)`。

- [ ] 为 `sys_user` 增加可空 `password_hash VARCHAR(255)`。
- [ ] 注册时校验手机号不存在、BCrypt 散列密码并记录协议。
- [ ] 登录时使用统一错误信息校验手机号与密码，成功后更新最后登录时间。
- [ ] 运行服务层和集成测试。

### Task 2: App 密码认证 API

**Files:**
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/auth/AppAuthController.java`
- Modify: `yingliu-api-app/src/main/java/com/yingliu/api/app/auth/AppAuthDtos.java`
- Modify: `yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`

**Interfaces:**
- Produces: `POST /api/v1/app/auth/register` 和 `POST /api/v1/app/auth/password/login`。

- [ ] 为注册与密码登录增加请求 DTO 和 Bean Validation。
- [ ] 注册成功返回空数据；密码登录签发现有格式的访问令牌。
- [ ] 覆盖成功注册登录、重复注册和错误密码。
- [ ] 运行后端定向测试。

### Task 3: C 端表单收敛

**Files:**
- Modify: `src/api/index.ts`
- Modify: `src/api/types.ts`
- Modify: `src/views/LoginView.vue`
- Modify: `e2e/auth.spec.ts`

**Interfaces:**
- Consumes: Task 2 的密码注册和登录接口。
- Produces: 密码登录、密码注册以及联系管理员提示。

- [ ] 删除验证码状态、倒计时、接口调用和登录方式切换。
- [ ] 隐藏企业微信入口但保留已有 OAuth 后端代码。
- [ ] 注册只提交手机号、密码、协议版本。
- [ ] 忘记密码显示联系管理员说明并提供返回登录操作。
- [ ] 运行类型检查、构建、Vitest 与浏览器交互验证。
