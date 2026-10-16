# 企业微信网页授权 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 自建应用的企业成员完成网页授权并取得现有 App 会话，所有部署参数在配置文件中集中管理。

**Architecture:** yingliu-wecom 封装已有 WxJava SDK；yingliu-service 维护一次性 state 和加密身份绑定；现有 AppAuthController 签发 App token。手机端保存授权发起状态、处理回跳，不在失败后自动放行开发账户。

**Tech Stack:** Java 17 / Spring Boot 3.3.12 / WxJava 4.6.0 / MyBatis / Redis / Vue 3。

## Global Constraints

- 后端在 D:/PrivateSoftware/gitClaude/ticktok-api，前端在 D:/PrivateSoftware/gitClaude/yingliu-c。原 shdy-tictok 业务源码不改。
- 不引入新第三方依赖，不修改既有数据库迁移；已有 sys_user_wecom_identity 列满足身份存储。
- Secret、应用 access_token 不返回前端、不写日志；真实凭据由部署者填入配置。
- state 随机、带过期、原子消费，并通过 HttpOnly Cookie 绑定授权浏览器；错误/过期/重放不签发会话。
- 仅接受企业成员 UserId；不自动以手机号或显示名合并账号。已有冻结/注销账号不能登录。

### Task 1: 后端授权和身份绑定

Files: yingliu-wecom 下 WecomProperties、WecomClient；service 的 DefaultAppAuthService、VerificationCodeStore 和两种实现；UserWeComIdentity/Mapper；AppAuthController；application-wecom.yml、各环境配置、.env.example。

Interfaces: WecomClient.authorizationUrl(String state)、exchange(String code) 返回 Member(corpId,userId,displayName)；VerificationCodeStore.consume(String key) 原子获取并删除。既有 /auth/wecom/authorize-url 返回 authorizeUrl/state，/callback 接收 code/state 并签发 App token。

- [x] 添加配置/客户端测试：缺配置返回 WECOM_UNAVAILABLE；链接包含 URL 编码回调、snsapi_base 和随机 state；mock-code 仅在显式 mock 模式有效；非成员拒绝。
- [x] 配置封装 WxCpServiceImpl 的 corpId/agentId/secret/redirectUri；SDK 缓存和刷新 token，网络错误转换为不含密钥的业务错误。
- [x] state 使用 32 字节 SecureRandom 与 TTL，Redis getAndDelete / 内存 remove 原子消费。Controller 设置与核对 Cookie，校验成功后清空。
- [x] 使用既有 corp_id + wecom_user_id_hmac 唯一索引，真实成员 ID AES 加密；绑定存在则复用原账号，不存在时事务创建。校验用户状态后登录。
- [x] 修改并增加集成断言：拿授权响应 Cookie/state 回调；再次使用、缺 Cookie、非法 code/state 拒绝；再次授权同一成员复用 userId，绑定只写一份；模拟接口错误不创建用户。

### Task 2: 手机端授权往返

Files: src/views/LoginView.vue、src/api/http.ts，以及相关测试。

- [x] LoginView 使用服务端返回 state；mock 请求也提交该 state。真实跳转前保存 sessionStorage state 和站内返回地址。
- [x] 回调校验 state，清除 code/state URL 参数并防重复提交，成功后返回站内路径；失败展示错误，不调用开发手机号放行。
- [x] HTTP 使用 credentials: include 以携带授权 Cookie。Secret 不进入任何 VITE 变量。
- [x] 测试成功跳转、错误 state、失败不放行；运行 npm run build，并以运行中 HTTP 接口验收本地 mock 登录，并在浏览器检查非法授权回跳分支。

### Task 3: 配置文档和验收

- [x] 独立 application-wecom.yml 写清 enabled、mock-enabled、corp-id、agent-id、secret、redirect-uri、state TTL、Cookie secure、身份加密密钥；生产强制关闭 mock。
- [x] 写明企业微信应用可见范围、网页授权可信域名、可信 IP、H5 /h5/login 回调与后端 POST callback 的区别。
- [x] 全量 Maven Docker 测试通过后打包并启动，验证新 JAR health 和授权接口。没有真实凭据时明确记录云端授权未实测。
- [x] 审查所有修改的认证边界、并发消费、身份复用与机密处理，修复必要问题后更新验证记录。
