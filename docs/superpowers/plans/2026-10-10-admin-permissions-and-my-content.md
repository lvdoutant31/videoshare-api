# 管理员页面操作授权、手机号绑定与我的内容实施计划

日期：2026-10-10；最近修订：2026-10-12。状态：长 ID 精度修复与超管代绑定已通过针对性测试，并完成三端发布构建；尚未部署。

## 目标与已确认需求

1. 超级管理员按菜单为每位普通管理员分配可见菜单，不通过角色分配授权。菜单权限控制侧栏和对应路由；该菜单需要的 API 权限由后台展开。
2. 管理员可以绑定手机号，用于 C 端登录；登录后能够识别其后台发布的内容为本人作品。
3. 自己发布的视频无需关注自己。自己的内容和作者主页不显示关注自己的入口，后端继续禁止自关注。
4. C 端“我的”增加本人新增内容的管理入口，覆盖视频、图文、纯文字，能够查看状态并执行允许的管理操作。

## 拟采用的实施规则

以下是准备实施时的默认规则，区别于用户明确给出的四项需求。

- 后台账号和 C 端账号保留各自登录会话与密码。绑定手机号时设置 C 端密码，不自动同步后台密码，也不让 C 端登录凭据访问后台接口。
- 本人内容按 `video.author_id` 与当前 C 端用户 ID 一致判断，包含其 B、C 两端创建的作品；不按用户名、手机号文本或 `created_by` 判断归属。
- 自己的关注按钮隐藏，但不伪造 `following=true`，不写入自关注关系，不改变粉丝或关注计数。
- 本次默认不改变“关注”信息流的内容范围；自己的作品通过“我的内容”直接访问，无需关注本人。
- 内容管理包含列表、本人详情/预览、编辑、重新提交审核、主动下架；按 2026-10-12 反馈移除 C 端删除按钮、调用及接口。
- 已发布内容修改标题、正文、分类或标签后撤出公开展示并重新审核。被管理员强制下架或限制的内容，作者不能自行恢复公开状态；更换视频源文件暂不支持。
- 不自动合并两个已有账号。手机号被另一 C 端账号占用时明确返回冲突；不得通过换绑覆盖历史作品或其他人的账号。若实际数据存在此情况，另行制定明确的关联/迁移方案后处理。

## 现有代码与关键差距

- 后台已有 `sys_permission`、角色权限关系、`AdminPermissionAuthorizer` 和 `AdminPermissionCache`，以及按权限判断的路由守卫。
- `yingliu-b/src/layouts/AdminSidebar.vue` 当前使用固定导航，尚未按权限过滤菜单。
- `yingliu-b/src/router.ts` 和后端 `AdminRuntimeLogController` 当前让运行日志与操作日志共用 `operation-log:view`，需要拆成独立的 `runtime-log:view`。
- `AdminAccountController` 已有管理员维护和角色分配接口，`AdminManagementPanel.vue` 当前没有直接分配权限的入口。
- `sys_admin_user.user_id` 已指向 `sys_user`。`DefaultVideoAdminService.currentAuthorId` 在后台首次发布时创建并关联作者资料，但该资料没有手机号和 C 端密码。
- C 端现有手机号密码登录、发布内容、图片上传功能可复用；发布内容进入 `PENDING_REVIEW`。
- `ProfileView.vue` 没有“我的内容”入口。公开作者内容查询只返回已审核且已发布的公开内容，不能用于查看自己的待审核、驳回或下架作品。
- `DefaultAppInteractionService.setFollow` 已禁止自关注；`FeedPlayer.vue`、`AuthorProfileView.vue` 尚未隐藏自己的关注入口。

## 一、页面与操作授权

### 业务行为

- 超管在管理员列表中点击“配置权限”，读取目标管理员当前权限并勾选保存。
- 没有页面查看权限：菜单隐藏、路由拒绝、页面读取接口拒绝。
- 菜单勾选是唯一分配项；后台将菜单映射为该菜单对应的 API 权限，运行日志等菜单可独立分配。
- 授权配置和超级管理员身份只能由超级管理员维护；旧角色分配接口不得成为普通管理员提升权限的旁路。
- 超级管理员保留完整权限，不能因勾选树配置而被锁出。保留最后一个有效超级管理员，启停及身份维护需要后端保护。
- 新建普通管理员默认没有业务页面权限，分配后生效。无可访问页面时显示明确的“暂无访问权限”，不循环跳工作台或登录页。
- 登录后的默认落点选择第一个有权限的页面；系统分类跳转、评论与举报页签、工作台快捷入口等同样按权限过滤。
- 授权撤销后，后端下一次请求立即拒绝；前端在重新加载会话、页面切换或接口返回权限不足时刷新菜单与按钮，不等待重新登录。

### 数据与接口建议

- 新增管理员直接权限关系表，如 `sys_admin_user_permission(admin_user_id, permission_id, created_at)`，联合唯一键与外键由 Flyway 新迁移创建。
- 迁移时将已有普通管理员的有效角色权限转换为初始直接授权，保留现有使用范围。普通管理员后续以直接授权为准，不将旧角色权限叠加回来，否则取消勾选仍会被旧角色重新授予。
- 超管身份继续使用现有内部 `SUPER_ADMIN` 标识；“普通管理员权限配置”不暴露身份升级选项。
- 新增权限目录读取、目标管理员授权读取及整组授权保存接口，建议放在 `/api/v1/admin/admins/permissions` 和 `/api/v1/admin/admins/{id}/permissions`，具体路径在实现时保持与现有路由不冲突。
- 复用现有授权缓存失效入口；保存授权的事务成功后使旧缓存失效。
- 新增 `runtime-log:view`，从运行日志路由、菜单、前端请求到后端接口统一使用。迁移时为此前可查看运行日志的账号保留此权限，之后可单独取消。
- 目录覆盖工作台、内容、审核、用户、管理员、评论、举报、分类、标签、操作日志、运行日志。只暴露当前真实存在的操作。
- 盘点共享操作码：如分类/标签的 `manage` 若同时控制新增、编辑、删除，应按实际按钮拆分对应权限，避免勾选“编辑”同时得到“删除”。维护接口检查与迁移映射，不创造未实现的业务操作。

### 受影响文件

- `yingliu-api-admin/.../admin/AdminAccountController.java`、`AdminAccountDtos.java`
- `yingliu-service/.../admin/AdminAccountService.java`
- `yingliu-api-admin/.../security/AdminPermissionCache.java`、`AdminPermissionAuthorizer.java`、`AdminSessionRevoker.java`
- `yingliu-api-admin/.../auth/AdminAuthService.java` 及现有各操作控制器
- `yingliu-api-admin/.../log/AdminRuntimeLogController.java`
- `yingliu-admin/src/main/resources/db/migration/` 新增迁移，不改历史迁移
- B 端 `src/features/admin/{model,repository}.ts`、`src/views/AdminManagementPanel.vue`
- B 端 `src/features/auth/`、`src/router.ts`、`src/layouts/AdminSidebar.vue` 及相关页面按钮/页签

### 验收

- 账号 A 有操作日志权限而没有运行日志权限：只显示操作日志；直接访问运行日志地址被拒绝，接口返回 403。
- 账号 B 仅有内容查看权限：可查列表，不能新增、编辑、审核、下架、删除；手工调用写接口被拒绝。
- 为在线账号撤销权限后，其旧会话不能继续调用接口；前端刷新后入口消失。
- 新建账号零授权不误继承角色权限；普通管理员不能修改授权或赋予自己超管身份。
- 两个管理员的授权互不污染；最后一个有效超管不能被停用或降级。

## 二、管理员绑定手机号

### 业务行为与数据保护

- 在管理员列表每行“操作”栏提供手机号绑定入口。普通管理员仅可为本人绑定；超级管理员可为选中的其他正常管理员绑定，后端独立校验超级管理员角色。绑定表单文案使用“绑定手机号”和“登录密码”。
- 首期复用当前登录体系，不新增真实短信服务；不将绑定结果误标记为已完成短信验证。
- 已有 `user_id` 的管理员：向同一作者账号补齐手机号和 C 端密码，保持用户 ID 和历史作品归属。
- 没有 `user_id` 的管理员：事务内创建 C 端用户并建立关联；复用后台首次发布的行锁机制，防止绑定和上传并发产生两套作者。
- 手机号已属于当前关联作者时保持同一关系；已属于其他账号则拒绝，不覆盖、不自动合并。
- 同一 C 端用户不得被两个不同后台管理员意外绑定。新增约束前先检查现有重复关联；发现异常不能删除数据来使迁移通过。
- 修改或设置 C 端密码后撤销旧 C 端会话；账号状态仍按各端现有规则校验。
- 管理员 DTO 返回脱敏手机号及绑定状态，不返回密码、手机号密文或摘要；审计日志不记录请求密码和完整手机号。

### 受影响文件

- `yingliu-domain/.../content/video/AdminUser.java`、`yingliu-domain/.../user/AppUser.java`（仅在需要字段时修改）
- `yingliu-service/.../content/video/mapper/{AdminUserMapper,VideoAuthorMapper}.java`
- `yingliu-service/.../app/{DefaultAppAuthService,AppAuthService}.java`、`mapper/AppUserMapper.java`
- `yingliu-api-admin/.../auth/` 的本人账号接口、DTO 与相应绑定服务
- B 端登录会话/个人账号入口及绑定表单
- C 端现有登录与个人资料接口（仅调整新增绑定字段或兼容处理）

### 验收

- 管理员先在 B 端发布再绑定，随后通过手机号登录 C 端：“我的内容”包含原 B 端作品，作者 ID 不变。
- 先绑定再从 B、C 两端发布：全部使用同一作者 ID。
- 错误后台密码、占用手机号、并发绑定、停用账号均得到明确失败，不产生半完成关系。
- 绑定不覆盖其他 C 端用户的作品、密码、关注、评论和收藏。

## 三、自己的作品隐藏关注入口

- 在 `FeedPlayer.vue` 和 `AuthorProfileView.vue` 使用当前登录用户 ID 与作者 ID 比较，统一按字符串比较，避免 Snowflake 大整数精度问题。
- 未登录及查看别人作品时继续使用正常关注流程。
- 自己主页可提供“管理我的内容”入口，指向本人管理页。
- 登录、退出、切换账号后更新判断，不沿用上一个账号的自身标记。
- 后端继续禁止自关注，不通过伪造关注状态影响关注信息流或计数。

验收：自己的视频、图文及作者主页不显示关注按钮；别人作品仍可关注；直接自关注请求仍被拒绝；粉丝/关注计数不变。

## 四、C 端我的内容管理

### 页面与接口建议

- `ProfileView.vue` 增加“我的内容”，新增 `/me/content` 列表与本人详情/编辑页面。
- 列表展示标题、内容类型、封面、创建时间、审核状态、发布状态及可执行操作。审核和发布是两个状态维度，保留两者，不折叠成容易丢失信息的单个状态。
- 覆盖待审核、审核通过未发布、已发布、已驳回、需修改、已下架及已有后台草稿；已删除内容不继续展示。
- 登录保护的本人内容列表、详情、编辑、提交审核、下架接口位于 `/api/v1/app/me/content`。内容、媒体、分类、标签 ID 在返回值中使用字符串，避免浏览器解析 19 位数字时丢失精度。
- 本人列表/详情可读取非公开记录；公开 Feed、公开作者主页和公开详情继续只返回原有允许公开的内容。
- 全部读写从 App 会话取用户 ID，按 `author_id` 校验归属，不能由客户端传入作者 ID 决定权限。
- 编辑复用现有富文本、分类标签、媒体和上传校验；从 `PublishView.vue` 提取确有复用需求的表单，避免复制整页逻辑或重写编辑器。
- 作者不能调用后台审核或发布接口；重新提交后走既有 B 端审核流程。
- 主动下架和治理下架需要可识别来源，必要时新增最小字段/迁移；治理下架、审核中及处理中状态按状态矩阵限制操作。
- 编辑、重提、下架与审核并发时通过版本号或条件更新检测冲突，不覆盖审核结果。修改使旧审核领取信息和定时发布状态失效。
- 本人内容不提供删除操作；已有删除记录继续按后台删除语义排除，保留数据库和媒体。

### 状态矩阵（实现时结合现有审核枚举细化）

| 状态 | 本人预览 | 编辑/重新提交 | 主动下架 |
| --- | --- | --- | --- |
| 草稿/已驳回/需修改 | 是 | 是，提交后待审核 | 否 |
| 待审核/已领取审核 | 是 | 根据领取和版本状态处理冲突，不覆盖处理中审核 | 否 |
| 审核通过未发布/定时发布 | 是 | 是，取消原发布计划并重新审核 | 否 |
| 已发布 | 是 | 是，撤下并重新审核 | 是 |
| 本人主动下架 | 是 | 是，恢复需要重新审核 | 否 |
| 管理员治理下架 | 是 | 不允许绕过治理恢复，按已有处罚规则限制 | 否 |

### 受影响文件

- `yingliu-api-app/.../content/video/` 新增本人管理控制器与 DTO
- `yingliu-service/.../content/video/` 新增本人内容用例，复用已有校验与数据结构
- `mapper/VideoMapper.java`、`VideoAdminService.java`、`DefaultVideoAdminService.java`（只提取必要共用逻辑）
- C 端 `src/api/content.ts`、`src/api/types.ts`、`src/router/index.ts`
- C 端 `src/views/ProfileView.vue`、`PublishView.vue`、新增本人列表/详情/编辑页面

### 验收

- C 端提交后即能在“我的内容”看到待审核作品，并能预览；游客和其他用户均无法读取。
- B 端历史作品和 C 端新作品在绑定用户的列表中统一展示。
- 修改已发布内容后公开流不再返回旧审核放行的记录，新内容重新进入审核。
- 本人下架后公开 Feed、详情、作者主页不再返回该内容；媒体和数据库历史记录保留。
- 用户 A 对用户 B 的详情、编辑、重提、下架请求全部被拒绝。
- 图文、纯文字、视频与旧播放器均无回归；版本冲突或审核并发得到明确提示。

## 实施顺序与检查点

- [x] 核对现有权限、账号关系、自关注和公开内容读取链路。
- [x] 保存需求、实施规则、文件范围和验收条件。
- [x] 完成当前工作区相关基线检查并记录结果。
- [x] 阶段 1：实现权限目录迁移、管理员直接授权、运行日志独立授权和后端权限校验；自动化测试未运行。
- [x] 阶段 2：实现 B 端菜单分配、菜单/路由过滤及无权限落点；菜单对应的 API 操作权限由后台展开。
- [x] 阶段 3：实现后台管理员手机号与 C 端密码一次性绑定、已有手机号冲突保护及关联作者账号复用；自动化测试未运行。
- [x] 阶段 4：隐藏本人关注入口；增加当前用户内容列表与仅本人可访问的内容详情接口。
- [x] 阶段 5：实现本人内容编辑后重新审核、已发布内容撤下；按反馈移除 C 端软删除。通过 HTTP 回归测试验证长 ID 往返，未运行浏览器联调。
- [x] 阶段 6：B/C 类型检查与三端生产构建通过，生成 Windows 版本发布 ZIP；自动化测试未运行。

后端重点覆盖授权隔离、角色权限不回流、缓存撤权、本人账号绑定、内容越权与审核并发。前端重点覆盖权限树依赖、运行日志独立隐藏、只读按钮、默认落点、自关注隐藏和本人内容状态操作。

## 工作区与交付约束

- 三个仓库当前均为普通工作目录，已有大量未提交工作：后端 `codex/count-consistency`，B 端 `feature/api-payload-encryption`，C 端 `codex/dashboard-data-fields`。
- 不 reset、stash、覆盖或提交无关改动。实施前核对现有改动和所需最新文件；从 HEAD 新建 worktree 不会自动包含这些在用功能，不能把旧 HEAD 当成此次实施基线。
- 本轮在三个既有工作区内增量实施，没有切换分支或写业务数据库；保留已有改动。
- 实施基线命令：B/C 各执行 `npm run typecheck`；后端执行 `AdminPermissionCacheTest`、`AdminPermissionAuthorizerTest`、`CurrentVideoAuthorTest`、`DefaultVideoAppServiceTest`。这些检查不等于全量测试或上线验证。
- 功能完成后使用 `deploy/build-release.ps1` 构建 B、C 和后端并生成版本 ZIP，遵循 `deploy/README.md` 的 BAT Windows 服务流程。
- 新迁移上线前备份现有数据库；保留数据库、媒体目录和运行配置，不重置数据、不把凭据写入日志或发布包。
- 服务器从新版本解压目录以管理员身份执行 `deploy-windows-service.bat deploy`；不使用 Docker 交付。

## 开发准备检查结果

2026-10-10 开发准备检查通过：

- B 端 `npm run typecheck`：通过。
- C 端 `npm run typecheck`：通过。
- 后端指定 Maven 测试：`BUILD SUCCESS`，4 个测试类共 17 项，失败 0、错误 0、跳过 0。
- `CurrentVideoAuthorTest` 4 项，`DefaultVideoAppServiceTest` 2 项，`AdminPermissionCacheTest` 3 项，`AdminPermissionAuthorizerTest` 8 项。

验证结果：B/C `npm run typecheck` 通过；后端 `mvn -pl yingliu-admin -am -DskipTests compile` 通过；反馈修订后再次执行 `deploy/build-release.ps1`，B/C 与后端生产构建均通过并生成新 ZIP。构建日志有 B 端 chunk 体积提示；不影响成功状态。自动化测试和浏览器联调未运行。尚未验证生产账号是否存在手机号占用、重复管理员关联或其他历史冲突；新迁移尚未应用到任何数据库，也未部署服务器。

发布包：`output/releases/yingliu-20261010-admin-menu-my-content-v2.zip`。服务器部署前仍需按本计划备份数据库、核对绑定冲突并遵循 BAT Windows 服务流程。

## 2026-10-12 修订验证与交付

- 修复本人内容直接返回数值型 Snowflake ID 的精度问题：列表、详情和编辑响应的内容、媒体、分类、标签 ID 均改用字符串。已丢失精度的旧 URL 无法还原，安装后需刷新“我的内容”列表重新取得正确 ID。
- 新增 `GET/PUT /api/v1/admin/admins/{adminId}/app-account`，仅超级管理员可查看绑定状态并为目标管理员绑定手机号。普通管理员继续通过本人认证接口管理自己的绑定。
- 操作栏支持超管选择其他管理员；弹窗显示目标用户名，统一使用“绑定手机号”“登录密码”文案。C 端删除入口、调用和接口保持移除。
- B/C 类型检查通过；`AppMyContentControllerTest` 3 项和 `AdminAccountPhoneBindingTest` 2 项均通过。覆盖详情、编辑、撤下长 ID 往返、状态冲突、内容归属拒绝，以及超管目标选择与普通管理员越权拒绝。
- 针对性测试命令：`mvn -pl yingliu-api-app,yingliu-api-admin -am -Dtest=AppMyContentControllerTest,AdminAccountPhoneBindingTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Djava.io.tmpdir=D:/PrivateSoftware/gitClaude/ticktok-api/tmp test`。本机采用无中文路径的 JVM 临时目录，避免 Mockito 在 Windows 中文用户临时目录下初始化失败。
- `deploy/build-release.ps1 -Version 20261012-content-id-superadmin-phone` 构建三端通过。最新包：`output/releases/yingliu-20261012-content-id-superadmin-phone.zip`。
- 公网入口为 `https://www.ysd-hsdy.com.cn:11443/videoshare`；未登录内容请求返回 401，服务器可达。未取得生产登录会话，未执行线上内容修改或绑定，也未安装发布包。
- 安装时先备份数据库并保留运行配置和媒体；从解压版本目录以管理员身份运行 `deploy-windows-service.bat deploy`，同步更新 B/C 静态文件。健康检查传入 `-BaseUrl https://www.ysd-hsdy.com.cn:11443`。

## 2026-10-12 编辑原记录与详情一致性修订

- 新增独立编辑路由 `/me/content/{contentId}/edit`，列表和个人详情均使用该入口。旧 `/publish?edit={id}` 链接继续转入编辑模式。
- 编辑表单必须成功读取原 ID 和版本后才能保存，加载失败、无效标识或路由切换不会将原表单当成新增内容提交。更新固定使用读取到的原 ID；已有视频编辑无需重新上传文件。
- 后端更新保持内容 ID 和原媒体关联，只有元数据、审核状态和版本发生变化。HTTP 回归测试验证更新条件包含原 ID，并且内容 Mapper 不调用 `insert`。
- 公开详情与“我的内容”详情共用 `ContentArticle.vue`，统一标题、作者、富文本、图片与表格排版；个人详情增加独立滚动容器，修复长正文被应用外层裁切的问题，移除图文封面的重复展示。
- 本人详情返回作者昵称和播放 URL；和公开详情一样优先使用就绪转码文件，原媒体与转码 URL 均按当前存储路径构建，保留原媒体信息供编辑使用。
- 前端 7 项针对性测试及 C 端类型检查通过；`AppMyContentControllerTest` 5 项通过。浏览器在 390×844 与 1440×900 视口通过两条回归流程，验证完整正文滚动、详情一致、一次原 ID PUT、零新增 POST、列表仍为一条原记录，无页面或控制台错误。
- Browser plugin not available，使用仓库现有 Playwright 流程。5173 在提供旧构建，当前源码验证使用临时配置启动独立的 `http://127.0.0.1:5181/videoshare/h5/`；截图位于 `C:/Users/吴骏/.codex/visualizations/2026/10/12/content-edit-qa-current/`。浏览器接口使用本地测试数据，不代表生产数据库已完成联调。
- `deploy/build-release.ps1 -Version 20261012-edit-original-detail-parity` 三端构建成功，最新发布包为 `output/releases/yingliu-20261012-edit-original-detail-parity.zip`；服务器尚未安装，已有重复记录未自动处理。
