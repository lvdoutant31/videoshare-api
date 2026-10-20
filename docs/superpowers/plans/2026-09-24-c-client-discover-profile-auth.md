# C 端发现、资料与账号体验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让发现页展示真实视频数据，修复底部发布按钮重叠，补齐可发现的资料编辑入口，并让账号登录/注册姓名字段与后端一致。

**Architecture:** 发现页复用现有公开推荐流接口，将真实已发布视频映射为热搜榜，避免新增重复的后端查询。资料编辑继续复用现有 `PATCH /api/v1/app/me`，在“我”页增加明确入口；注册接口新增必填姓名并写入 `sys_user.nickname`，登录仍使用手机号和密码。

**Tech Stack:** Vue 3、TypeScript、Pinia、Vant、Spring Boot、MyBatis-Plus、Vitest、Playwright、JUnit/MockMvc。

## Global Constraints

- C 端不启用短信验证码和企业微信登录。
- 登录凭证保持手机号和密码；姓名不是唯一标识，不参与登录校验。
- 真实环境不得展示硬编码热搜数据。
- 仅修改本轮需求涉及的文件，保留工作区其他已有改动。

---

### Task 1: 真实热搜榜

**Files:**
- Modify: `yingliu-c/src/views/DiscoverView.vue`
- Modify: `yingliu-c/src/api/index.ts`
- Modify: `yingliu-c/src/api/types.ts`
- Test: `yingliu-c/src/views/DiscoverView.spec.ts`

**Interfaces:**
- Consumes: `getRecommendFeed(null, 20)` 返回 `CursorPage<FeedItem>`。
- Produces: `getTrendingVideos(limit: number): Promise<TrendingItem[]>`，其中 `TrendingItem` 包含 `videoId`、`title`、`badge`、`coverUrl`。

- [ ] **Step 1: 写发现页真实数据测试**

验证挂载后调用趋势接口，加载态结束后渲染接口返回标题；接口失败展示可重试状态，不能回退到硬编码榜单。

- [ ] **Step 2: 运行测试并确认旧实现失败**

Run: `npm test -- DiscoverView.spec.ts`
Expected: FAIL，旧页面未调用 API。

- [ ] **Step 3: 实现趋势数据适配与页面状态**

在 API 层复用推荐流真实接口并映射标题、封面和基于真实互动数生成的榜单标签；发现页使用 `onMounted` 加载并处理 loading/error/empty。

- [ ] **Step 4: 运行发现页测试**

Run: `npm test -- DiscoverView.spec.ts`
Expected: PASS。

### Task 2: 底部发布按钮不重叠

**Files:**
- Modify: `yingliu-c/src/styles/index.css`
- Test: `yingliu-c/e2e/discover-profile-auth.spec.ts`

**Interfaces:**
- Consumes: `.bottom-nav .create-button` 现有 DOM。
- Produces: 完全位于导航栏布局盒内的 40px 发布按钮。

- [ ] **Step 1: 调整按钮定位**

移除负 top/margin 和双层伪元素偏移，保留视觉强调但确保按钮在 47px 导航栏内垂直居中。

- [ ] **Step 2: 用 Playwright 检查包围盒**

断言发布按钮可见，且其边界不超出底部导航的上、下边界。

### Task 3: “我”页资料编辑入口

**Files:**
- Modify: `yingliu-c/src/views/ProfileView.vue`
- Modify: `yingliu-c/src/views/AccountSettingsView.vue`
- Test: `yingliu-c/e2e/discover-profile-auth.spec.ts`

**Interfaces:**
- Consumes: 已有 `/me/settings` 路由和 `auth.patchProfile({ nickname, bio })`。
- Produces: “编辑资料”显式按钮，以及姓名、个人简介编辑表单。

- [ ] **Step 1: 增加显式入口**

在“我”页用户卡片上增加“编辑资料”按钮，导航至 `/me/settings`。

- [ ] **Step 2: 调整资料字段文案**

将昵称字段显示为姓名，保留 1–30 字校验和简介编辑；保存后 Pinia 用户信息即时更新。

- [ ] **Step 3: 验证入口与保存交互**

使用 Playwright 从“我”页进入设置，修改姓名并确认请求与页面状态更新。

### Task 4: 注册姓名与账号登录文案

**Files:**
- Modify: `ticktok-api/yingliu-api-app/src/main/java/com/yingliu/api/app/auth/AppAuthController.java`
- Modify: `ticktok-api/yingliu-service/src/main/java/com/yingliu/service/app/AppAuthService.java`
- Modify: `ticktok-api/yingliu-service/src/main/java/com/yingliu/service/app/DefaultAppAuthService.java`
- Modify: `ticktok-api/yingliu-admin/src/test/java/com/yingliu/api/app/AppApiIntegrationTest.java`
- Modify: `yingliu-c/src/views/LoginView.vue`
- Modify: `yingliu-c/src/api/index.ts`
- Modify: `yingliu-c/src/views/LoginView.spec.ts`

**Interfaces:**
- Produces: `registerByPassword(String phone, String password, String name, String agreementVersion)`。
- Produces: `POST /api/v1/app/auth/register` 请求体 `{ phone, password, name, agreementVersion }`。

- [ ] **Step 1: 更新后端失败测试**

注册测试传入姓名并断言登录响应的 `user.nickname` 等于该姓名，同时验证空姓名返回 400。

- [ ] **Step 2: 实现后端姓名写入**

控制器对姓名使用 `@NotBlank @Size(max=30)`；服务层 trim 后作为新用户昵称写入，密码登录契约保持不变。

- [ ] **Step 3: 更新登录注册 UI 与 API**

登录页标题显示“账号登录”；注册模式增加必填姓名输入框，并随注册请求提交。账号设置的登录方式显示“账号登录 / 手机号 + 密码”。

- [ ] **Step 4: 运行全套验证**

Run: `npm run typecheck && npm test && npm run build && npm run e2e -- e2e/discover-profile-auth.spec.ts`
Expected: 全部通过。

Run: `.\\mvnw.cmd -pl yingliu-admin -am "-Dtest=AppApiIntegrationTest#passwordRegistrationAndLoginCreateSession" "-Dsurefire.failIfNoSpecifiedTests=false" test`
Expected: BUILD SUCCESS；若 Docker 不可用，明确记录 Testcontainers 跳过。
