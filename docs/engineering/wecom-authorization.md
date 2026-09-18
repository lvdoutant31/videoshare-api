# 企业微信网页授权配置

本功能用于企业微信自建应用内的企业成员登录手机端。后端通过已有 WxJava SDK 交换授权码，创建或复用平台账号并签发现有 App 会话。

## 需要填写的配置

配置集中在 `yingliu-admin/src/main/resources/application-wecom.yml`，可以直接修改 YAML，也可以通过同名环境变量覆盖。本地 `local` 环境从启动工作目录读取 `.env`，模板见项目根目录 `.env.example`。真实 Secret 和身份密钥不要提交到版本库。

| 环境变量 | 含义 |
| --- | --- |
| `WECOM_ENABLED=true` | 开启真实企业微信授权 |
| `WECOM_MOCK_ENABLED=false` | 关闭本地模拟登录；兼容旧变量 `YINGLIU_WECOM_MOCK_ENABLED`，新变量优先 |
| `WECOM_CORP_ID` | 企业 ID |
| `WECOM_AGENT_ID` | 自建应用 AgentId |
| `WECOM_SECRET` | 对应自建应用 Secret，只供后端使用 |
| `WECOM_REDIRECT_URI` | 手机端 HTTPS 登录页，例如 `https://video.example.com/h5/login` |
| `WECOM_IDENTITY_KEY` | 至少 32 字符的随机密钥，用于成员 ID 加密及身份摘要；上线后保持稳定并妥善备份 |
| `WECOM_STATE_TTL_SECONDS=300` | 授权状态有效期，范围 60～1800 秒 |
| `WECOM_COOKIE_SECURE=true` | HTTPS 部署开启；仅本地 HTTP 模拟联调设为 false |

`prod` 环境固定关闭模拟登录并启用 Secure Cookie。默认关闭模拟登录；本地可在 `.env` 中设置 `WECOM_MOCK_ENABLED=true` 显式开启模拟，真实联调应关闭模拟。更换企业身份密钥会改变成员匹配摘要，不能直接替换生产密钥，应先安排数据迁移。

## 企业微信后台与部署

1. 创建或选用企业自建应用，填写企业 ID、应用 AgentId、对应 Secret。
2. 将需要登录的成员纳入应用可见范围。
3. 为应用配置网页授权可信域名，与 `WECOM_REDIRECT_URI` 的域名对应，并完成管理后台要求的域名校验。
4. 按管理后台要求配置企业可信 IP，填写后端服务器访问企业微信 API 时使用的公网出口 IP。
5. 手机端和 API 使用同一个 HTTPS 域名：`/h5/` 提供手机端页面，`/api/` 反向代理后端。回跳须保持原浏览器会话，使 sessionStorage 和授权 Cookie 可用。

回跳地址是前端 `/h5/login`，不是后端 `/api/v1/app/auth/wecom/callback`。后端 callback 是接收 JSON 的 POST 接口。此流程不需要配置企业微信消息推送的 Token 或 EncodingAESKey。

## 登录流程

1. 前端 GET `/api/v1/app/auth/wecom/authorize-url`，接收授权链接、随机 state 和 HttpOnly Cookie。
2. 前端保存 state 与站内返回地址，跳转企业微信网页授权页。
3. 企业微信回跳手机端登录页并携带 code、state。前端校验 state，清理地址中的授权参数，再 POST code、state 到后端 callback。
4. 后端核对 Cookie，并原子消费带有效期的 state；通过 SDK 获取企业成员 UserId。SDK 在后端管理应用 access_token。
5. 按企业 ID 和成员摘要查找绑定，首次登录事务创建账号和加密身份。已有账号复用，封禁、注销、删除、仍在停用期间或已解绑的账号拒绝登录。

只接收企业成员 UserId，不以外部联系人的 OpenId 建立账号，不通过昵称或手机号自动合并已有账号。静默授权不会额外请求成员姓名或手机号，新真实账号默认昵称为“企业成员”。

## 本地测试与常见错误

本地模拟无需真实企业配置，使用 `local` 环境且 `WECOM_MOCK_ENABLED=true`，页面仍须先获取随机 state；固定 state 或重放回调会失败。模拟失败不会自动通过开发手机号登录。

- `WECOM_UNAVAILABLE`：未开启、缺少配置、回跳地址不是有效 HTTPS URL，或上游请求失败。核对配置、出口 IP、网络以及对应应用 Secret。
- `WECOM_STATE_INVALID`：状态过期、重复使用、Cookie 丢失或回跳浏览器不同。重新从登录按钮发起授权，并核对同源 HTTPS 部署。
- `WECOM_CODE_INVALID`：授权码错误或已使用，重新发起授权。
- `WECOM_MEMBER_REQUIRED`：没有获得企业成员身份，检查登录成员与应用可见范围。
- `WECOM_BINDING_INACTIVE`：该身份已被管理员解绑，需要管理员处理。

未提供真实企业凭据和可信域名时，只能验证本地模拟、SDK 封装和接口回归，无法代替企业微信客户端内的真实授权验收。


## 本次验证（2026-09-06）

- 后端 62 项测试全部通过，未跳过；含 Docker MySQL 身份绑定、账号复用、封禁和解绑拒绝测试。
- 手机端 36 项测试通过，生产构建通过。
- 本地运行中 API 验证：获取授权成功、缺 Cookie 401、模拟登录成功、重复 state 401、同成员复用账号；测试会话已登出。
- 内置浏览器验证：登录页正常，非法授权回跳显示状态错误并移除 code/state，控制台无错误。
- 当前验收进程使用 local 模拟模式。真实部署按前文填写配置并移除启动参数中的模拟覆盖。
- 本地临时验收账号保留在开发库，未删除已有业务数据。真实企业微信客户端授权尚需有效企业配置和可信域名验收。
