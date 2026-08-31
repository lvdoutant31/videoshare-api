# 映流短视频系统 API 接口清单

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V1.0 |
| Base URL | `/api/v1` |
| 认证 | App 与后台使用独立 Sa-Token 域；媒体播放使用短期播放凭证 |
| 上游依据 | PRD V1.1、技术选型、数据库设计 |

## 1. 通用契约

### 1.1 响应体

```json
{
  "code": "OK",
  "message": "",
  "data": {},
  "traceId": "8a6f1e1596c84ffaad7cfd7b7a326b8a"
}
```

- 成功：HTTP `200/201/202/204`，业务 `code=OK`。
- 失败：HTTP 表示传输和认证语义，业务 `code` 表示稳定错误码；客户端不得依赖异常文本。
- 列表分页返回：`data.items`、`data.nextCursor`（信息流）或 `data.page/data.pageSize/data.total`（后台表格）。
- 时间使用 ISO 8601 UTC，例如 `2026-08-27T09:21:12.123Z`；ID 以字符串返回，避免 JavaScript 精度丢失。
- 写接口接受 `Idempotency-Key` 请求头；点赞、收藏、关注等目标状态写入即使未携带该头也必须幂等。

### 1.2 认证与错误码

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 401 | `AUTH_REQUIRED` / `TOKEN_EXPIRED` | 未登录、会话无效 |
| 403 | `PERMISSION_DENIED` / `ACCOUNT_MUTED` / `ACCOUNT_SUSPENDED` | 无权限、禁言或封禁 |
| 404 | `VIDEO_NOT_FOUND` / `RESOURCE_NOT_FOUND` | 不存在或无可见权限时统一返回资源不存在 |
| 409 | `STATE_CONFLICT` / `BINDING_CONFLICT` / `AUDIT_CLAIMED` | 状态冲突、企微绑定冲突、审核已领取 |
| 413 | `UPLOAD_SIZE_EXCEEDED` | 上传大小超限 |
| 422 | `VALIDATION_FAILED` / `SENSITIVE_CONTENT` | 字段校验、敏感词命中 |
| 429 | `RATE_LIMITED` | 登录、互动、分享等限流 |
| 503 | `UPLOAD_PAUSED_DISK_FULL` / `WECOM_UNAVAILABLE` | 磁盘容量保护、外部依赖不可用 |

## 2. 手机端 App API

### 2.1 登录与账户 `/app/auth`、`/app/me`

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/app/auth/phone/send-code` | 否 | 发送手机号验证码；按 IP、设备、手机号摘要限流 |
| `POST` | `/app/auth/phone/login` | 否 | 手机号验证码登录/注册，返回 App 会话与当前用户 |
| `GET` | `/app/auth/wecom/authorize-url` | 否 | 生成企微 OAuth 跳转地址和一次性 `state` |
| `POST` | `/app/auth/wecom/callback` | 否 | 处理已校验的 OAuth 回调，签发本平台会话 |
| `POST` | `/app/auth/logout` | App | 退出当前设备会话 |
| `GET` | `/app/me` | App | 当前用户资料、互动汇总、企微绑定状态 |
| `PATCH` | `/app/me` | App | 修改头像、昵称、简介 |
| `POST` | `/app/me/phone/bind/send-code` | App | 给绑定手机号发送验证码 |
| `PUT` | `/app/me/phone` | App | 验证后绑定/替换手机号 |
| `DELETE` | `/app/me/wecom-binding` | App | 解绑企微身份；必须仍保留可用登录方式 |
| `POST` | `/app/me/cancellation` | App | 发起账号注销申请 |
| `GET` | `/app/me/agreements` | App | 已同意协议版本 |
| `PUT` | `/app/me/agreements/{type}` | App | 同意新版本用户协议/隐私政策 |

`POST /app/auth/phone/login`：

```json
{ "phone": "13800138000", "code": "123456", "agreementVersion": "2026-08" }
```

```json
{
  "code": "OK",
  "data": {
    "accessToken": "app-token",
    "expiresIn": 2592000,
    "user": { "id": "10001", "nickname": "林知夏", "status": "NORMAL" }
  },
  "traceId": "..."
}
```

企微回调的授权码、access token 与成员 ID 均不得返回前端或写入访问日志。

### 2.2 推荐流与播放 `/app/feed`、`/app/videos`

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/app/feed/recommend` | 可选 | 游标分页推荐流；只返回可见已发布视频 |
| `GET` | `/app/videos/{videoId}` | 可选 | 视频详情与当前用户互动状态 |
| `POST` | `/app/videos/{videoId}/play-token` | 可选 | 签发 5 分钟播放凭证，先校验视频可见性与下架状态 |
| `GET` | `/app/videos/{videoId}/related` | 可选 | 同作者/同分类候选，V1 可按规则简单返回 |
| `POST` | `/app/videos/{videoId}/report` | App | 举报视频 |

推荐流请求：`GET /app/feed/recommend?cursor=...&limit=5`，`limit` 范围 `1-10`。

推荐流单项最小返回：

```json
{
  "id": "90001",
  "title": "客户现场安全操作要点",
  "description": "...",
  "coverUrl": "https://.../cover-token",
  "durationMs": 86400,
  "author": { "id": "10001", "nickname": "安全培训组", "avatarUrl": "...", "isFollowing": false },
  "stats": { "likeCount": 1260, "commentCount": 98, "favoriteCount": 320 },
  "viewerState": { "liked": false, "favorited": false },
  "tags": ["安全培训"],
  "publishedAt": "2026-08-27T09:00:00Z"
}
```

播放凭证响应：

```json
{ "data": { "playUrl": "/media/play/opaque-short-token", "expiresAt": "2026-08-27T09:05:00Z" } }
```

`/media/play/{token}` 不属于 `/api/v1`，由 Nginx 接收；业务校验通过后使用 `X-Accel-Redirect` 返回内部媒体路径，支持 Range。令牌失效、视频下架或无权限时均不得返回实际文件位置。

### 2.3 点赞、收藏、关注 `/app/videos`、`/app/users`

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `PUT` | `/app/videos/{videoId}/like` | App | 请求体 `{ "active": true }`，设置目标点赞状态 |
| `PUT` | `/app/videos/{videoId}/favorite` | App | 请求体 `{ "active": true }`，设置目标收藏状态 |
| `PUT` | `/app/users/{userId}/follow` | App | 请求体 `{ "active": true }`，设置关注状态 |
| `GET` | `/app/me/favorites` | App | 收藏列表，游标分页 |
| `GET` | `/app/me/likes` | App | 点赞列表，游标分页 |
| `GET` | `/app/me/following` | App | 我关注的作者列表 |
| `GET` | `/app/me/followers` | App | 我的粉丝列表 |
| `GET` | `/app/users/{userId}` | 可选 | 作者主页与公开视频网格 |
| `GET` | `/app/users/{userId}/videos` | 可选 | 作者公开视频，游标分页 |

写入成功均返回服务端权威状态：

```json
{ "data": { "active": true, "count": 1261 } }
```

客户端可乐观更新；网络或业务失败时须恢复本地状态。禁止关注自身。

### 2.4 评论 `/app/videos/{videoId}/comments`、`/app/comments`

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/app/videos/{videoId}/comments` | 可选 | 一级评论，`sort=HOT|LATEST`，游标分页 |
| `GET` | `/app/comments/{commentId}/replies` | 可选 | 二级回复，按时间正序 |
| `POST` | `/app/videos/{videoId}/comments` | App | 发布一级评论或回复；可能返回待审 |
| `PUT` | `/app/comments/{commentId}/like` | App | 目标状态 `{ "active": true }` |
| `DELETE` | `/app/comments/{commentId}` | App | 删除本人评论 |
| `POST` | `/app/comments/{commentId}/report` | App | 举报评论 |

发布评论：

```json
{ "content": "建议在进场前完成设备检查。", "replyToCommentId": null }
```

结果：`{ "id": "...", "status": "NORMAL" }` 或 `{ "id": "...", "status": "PENDING", "message": "评论正在审核" }`。禁言账号返回 `403 ACCOUNT_MUTED`。

### 2.5 历史、分享与举报

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/app/me/history` | App | 观看历史，游标分页 |
| `DELETE` | `/app/me/history` | App | 清空可恢复展示历史 |
| `GET` | `/app/videos/{videoId}/share-options` | App | 返回当前环境可用/降级的分享渠道与落地信息 |
| `POST` | `/app/videos/{videoId}/shares/internal` | App | 向最多 20 个站内用户发送视频卡片，返回逐项结果 |
| `GET` | `/app/share-recipients` | App | 按昵称/用户 ID 搜索候选；`q`、`cursor`、`limit` |
| `GET` | `/app/share-recipients/recent` | App | 最近转发对象 |
| `GET` | `/app/share-inbox` | App | 收件箱列表 |
| `GET` | `/app/share-inbox/{inboxId}` | App | 收件箱卡片详情；打开前实时校验视频可见性 |
| `POST` | `/app/videos/{videoId}/share-events` | 可选 | 记录外部分享点击、渠道选择、落地页访问 |
| `POST` | `/app/users/{userId}/report` | App | 举报用户 |

站内转发请求：

```json
{ "recipientIds": ["10002", "10003"] }
```

站内转发响应必须逐收件人报告成功或失败，批量部分失败的 HTTP 仍可为 `200`：

```json
{
  "data": {
    "batchId": "70001",
    "results": [
      { "recipientId": "10002", "success": true, "inboxId": "80001" },
      { "recipientId": "10003", "success": false, "code": "RECIPIENT_SUSPENDED" }
    ]
  }
}
```

## 3. 后台认证与工作台 API

### 3.1 后台认证 `/admin/auth`

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/admin/auth/password/login` | 否 | 账号密码 + 验证码登录 |
| `GET` | `/admin/auth/wecom/qr-session` | 否 | 创建企微扫码会话，返回二维码数据与轮询标识 |
| `GET` | `/admin/auth/wecom/qr-session/{sessionId}` | 否 | 轮询扫码状态；成功只返回一次性交换码 |
| `POST` | `/admin/auth/wecom/exchange` | 否 | 用交换码换取后台会话 |
| `POST` | `/admin/auth/logout` | Admin | 退出当前后台会话 |
| `GET` | `/admin/auth/me` | Admin | 当前后台账号、角色、权限码 |
| `PUT` | `/admin/auth/password` | Admin | 修改当前账号密码 |

企微 PC 扫码回调由 `/internal/wecom/*` 接收，不能直接暴露企微授权数据给浏览器。成功登录还必须验证后台角色与账号状态。

### 3.2 工作台 `/admin/dashboard`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/dashboard/summary` | `dashboard:view` | 今日新增用户、活跃用户、视频、播放、互动、待办与最近更新时间 |
| `GET` | `/admin/dashboard/trends` | `dashboard:view` | `range=7d|30d`，指标趋势 |
| `GET` | `/admin/dashboard/todos` | `dashboard:view` | 待审核、待处理举报、转码失败、容量告警 |
| `GET` | `/admin/dashboard/storage` | `storage:view` | 本地媒体盘容量、备份状态、阈值状态 |

## 4. 后台内容生产与审核 API

### 4.1 视频管理 `/admin/videos`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/videos` | `video:view` | 页码列表，标题/ID/作者/分类/状态/时间筛选与排序 |
| `POST` | `/admin/videos` | `video:create` | 从已完成原始媒体创建草稿 |
| `GET` | `/admin/videos/{videoId}` | `video:view` | 视频详情、媒体、转码、审核与统计摘要 |
| `PATCH` | `/admin/videos/{videoId}` | `video:edit` | 编辑资料；已发布核心内容变更后重入审核 |
| `POST` | `/admin/videos/{videoId}/submit-review` | `video:submit` | 提交审核 |
| `POST` | `/admin/videos/{videoId}/off-shelf` | `video:off-shelf` | 下架，原因必填 |
| `POST` | `/admin/videos/{videoId}/restore` | `video:restore` | 恢复或重新提交审核 |
| `DELETE` | `/admin/videos/{videoId}` | `video:delete` | 逻辑删除，原因必填 |
| `POST` | `/admin/videos/batch/off-shelf` | `video:batch-off-shelf` | 批量下架，异步任务 + 逐项结果 |
| `POST` | `/admin/videos/batch/restore` | `video:batch-restore` | 批量恢复，异步任务 + 逐项结果 |
| `POST` | `/admin/videos/{videoId}/transcodes/{definition}/retry` | `video:retry-transcode` | 重试失败转码 |
| `GET` | `/admin/videos/{videoId}/preview-token` | `video:view` | 审核/运营预览短期媒体令牌 |

创建草稿请求示例：

```json
{
  "authorId": "10001",
  "originalMediaId": "50001",
  "coverMediaId": "50002",
  "title": "新员工入职培训",
  "description": "...",
  "categoryIds": ["10"],
  "tagIds": ["20", "21"],
  "visibility": "PUBLIC",
  "scheduledAt": null
}
```

### 4.2 本地分片上传 `/admin/media/uploads`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/admin/media/uploads` | `media:upload` | 初始化上传会话，校验大小、格式、时长声明 |
| `GET` | `/admin/media/uploads/{uploadId}` | `media:upload` | 查询状态与已上传分片位图 |
| `PUT` | `/admin/media/uploads/{uploadId}/chunks/{chunkIndex}` | `media:upload` | 上传单分片；二进制 body，携带 `Content-Range`、分片哈希 |
| `POST` | `/admin/media/uploads/{uploadId}/complete` | `media:upload` | 请求合并、SHA-256 校验、创建转码任务 |
| `DELETE` | `/admin/media/uploads/{uploadId}` | `media:upload` | 取消上传与清理临时文件 |
| `POST` | `/admin/media/covers` | `media:upload` | 上传/替换封面 |

初始化响应包含服务端生成的 `uploadId`、`chunkSize`、`expiresAt`。上传接口仅接受 `uploadId + chunkIndex`，禁止任何客户端路径参数。

### 4.3 视频审核 `/admin/reviews/videos`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/reviews/videos` | `review:video:view` | 审核队列，按时间、风险、作者历史筛选 |
| `POST` | `/admin/reviews/videos/{videoId}/claim` | `review:video:claim` | 领取审核任务，短时锁定 |
| `POST` | `/admin/reviews/videos/{videoId}/approve` | `review:video:approve` | 通过，可指定立即或定时发布 |
| `POST` | `/admin/reviews/videos/{videoId}/reject` | `review:video:reject` | 驳回，原因必填 |
| `POST` | `/admin/reviews/videos/{videoId}/request-revision` | `review:video:revision` | 要求修改，原因必填 |
| `GET` | `/admin/reviews/videos/{videoId}/history` | `review:video:view` | 审核历史与机器预检快照 |

审核动作使用乐观版本或领取令牌，避免锁过期后覆盖其他审核人的结果。

## 5. 后台用户、评论与举报 API

### 5.1 用户管理 `/admin/users`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/users` | `user:view` | 用户列表，手机号仅受控精确/模糊查询，响应脱敏 |
| `GET` | `/admin/users/{userId}` | `user:view` | 用户资料、企微绑定摘要、违规与设备摘要 |
| `GET` | `/admin/users/{userId}/contents` | `user:view` | 用户视频、评论、举报、被举报、处罚历史 |
| `POST` | `/admin/users/{userId}/mute` | `user:mute` | 禁言，原因与截止时间必填 |
| `POST` | `/admin/users/{userId}/suspend` | `user:suspend` | 临时封禁，原因与截止时间必填 |
| `POST` | `/admin/users/{userId}/ban` | `user:ban` | 永久封禁，原因必填，二次确认 |
| `POST` | `/admin/users/{userId}/penalties/{penaltyId}/revoke` | `user:penalty:revoke` | 提前解除处罚，原因必填 |
| `POST` | `/admin/users/{userId}/wecom-binding/unbind` | `user:wecom:unbind` | 管理员解绑企微身份，写审计日志 |

### 5.2 评论管理 `/admin/comments`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/comments` | `comment:view` | 搜索、视频/用户/状态/时间筛选 |
| `GET` | `/admin/comments/{commentId}` | `comment:view` | 上下文、回复链、风险与处置记录 |
| `POST` | `/admin/comments/{commentId}/hide` | `comment:hide` | 隐藏，原因必填 |
| `POST` | `/admin/comments/{commentId}/restore` | `comment:restore` | 恢复正常 |
| `DELETE` | `/admin/comments/{commentId}` | `comment:delete` | 删除，原因必填；一级评论会前台隐藏回复 |
| `POST` | `/admin/comments/batch/handle` | `comment:batch-handle` | 批量处置，异步逐项结果 |

### 5.3 举报处理 `/admin/reports`

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/reports` | `report:view` | 举报队列，按对象、状态、风险、时间筛选 |
| `GET` | `/admin/reports/{reportId}` | `report:view` | 原对象上下文、快照和相关历史 |
| `POST` | `/admin/reports/{reportId}/claim` | `report:claim` | 领取处理任务 |
| `POST` | `/admin/reports/{reportId}/resolve` | `report:resolve` | 处置举报，可联动隐藏/下架/处罚 |
| `POST` | `/admin/reports/{reportId}/reject` | `report:reject` | 驳回举报，处理说明必填 |

`resolve` 事务内更新报告结果与业务对象状态，并写审计日志；跨对象的异步操作返回任务 ID。

## 6. 后台系统配置与审计 API

### 6.1 分类与标签

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET/POST` | `/admin/categories` | `category:view/manage` | 列表与创建 |
| `PATCH/DELETE` | `/admin/categories/{categoryId}` | `category:manage` | 修改、停用或逻辑删除 |
| `GET/POST` | `/admin/tags` | `tag:view/manage` | 标签列表与创建 |
| `PATCH` | `/admin/tags/{tagId}` | `tag:manage` | 修改、启停 |
| `POST` | `/admin/tags/{tagId}/merge` | `tag:manage` | 合并到目标标签 |

### 6.2 配置、角色与审计

| 方法 | 路径 | 权限码 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/admin/configs` | `config:view` | 获取配置，机密字段脱敏 |
| `PUT` | `/admin/configs/{configKey}` | `config:edit` | 更新配置，格式校验、版本号与审计 |
| `POST` | `/admin/configs/{configKey}/rollback` | `config:rollback` | 回滚关键配置版本 |
| `GET/POST/PATCH` | `/admin/admin-users` | `admin-user:manage` | 后台账号的查询、新建、停用、重置密码 |
| `GET/POST/PATCH` | `/admin/roles` | `role:manage` | 角色管理 |
| `GET` | `/admin/permissions` | `permission:view` | 权限树读取 |
| `PUT` | `/admin/roles/{roleId}/permissions` | `role:manage` | 设置角色权限 |
| `GET` | `/admin/audit-logs` | `audit:view` | 按操作者、对象、动作、时间检索审计日志 |
| `GET` | `/admin/async-tasks` | `task:view` | 转码、发布、备份等任务状态 |
| `POST` | `/admin/async-tasks/{taskId}/retry` | `task:retry` | 人工重试失败任务 |

高风险操作（永久封禁、逻辑删除、批量下架、密钥修改、角色权限变更）请求体必须包含 `reason` 和 `confirmationToken`。后者由前端二次确认对话框申请：`POST /admin/confirmations`，有效期 60 秒且一次性使用。

## 7. 企微内部回调与分享落地页

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `GET/POST` | `/internal/wecom/oauth/callback` | 企微签名 + state | 手机端 OAuth 回调；校验来源、state、企业范围 |
| `GET/POST` | `/internal/wecom/admin-qr/callback` | 企微签名 + 会话关联 | PC 扫码回调 |
| `POST` | `/internal/wecom/webhook` | 企微签名 | 仅在配置的事件回调需要时启用 |
| `GET` | `/share/videos/{shareCode}` | 可选 | 受控分享落地页数据；不返回内部媒体位置 |
| `POST` | `/share/videos/{shareCode}/open-events` | 可选 | 记录落地页打开事件 |

`/internal/*` 路由不在 OpenAPI 的对外 App/Admin 分组中，Nginx 应限流并只允许必要方法。回调参数与签名验证失败写安全日志但不泄漏判定细节。

## 8. 埋点与运维 API

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/analytics/events/batch` | 可选 | 批量接收白名单事件；最大 50 条/请求；服务端补充接收时间与 traceId |
| `GET` | `/health` | 否（内网） | 应用存活探针 |
| `GET` | `/actuator/health` | 否（内网或监控网络） | Spring Boot 健康状态 |
| `GET` | `/actuator/metrics` | 否（内网或监控网络） | 指标端点，生产环境不得公开到互联网 |

事件对象示例：

```json
{
  "events": [
    {
      "eventName": "video_play_progress",
      "occurredAt": "2026-08-27T09:23:10.000Z",
      "anonymousSessionId": "b8b90d95-ef4f-4b89-8cf6-3174b33537fe",
      "videoId": "90001",
      "pageSource": "recommend_feed",
      "networkType": "WIFI",
      "properties": { "positionMs": 2000 }
    }
  ]
}
```

服务端仅接收事件白名单字段；`properties` 按事件类型校验，拒绝令牌、手机号、完整 IP、企微身份等敏感字段。

## 9. OpenAPI 分组与前端生成

- `app-api`：`/api/v1/app/**`，手机端 API client 与 TypeScript 类型由 OpenAPI 生成。
- `admin-api`：`/api/v1/admin/**`，后台 API client 与 TypeScript 类型由 OpenAPI 生成。
- `integration-api`：`/api/v1/internal/**` 和 `/media/**`，仅后端维护，不向普通前端暴露。
- 为每个写接口定义请求、成功响应、稳定错误码和鉴权要求；接口调整必须先升级 OpenAPI，再执行前后端契约测试。
- 路由版本按 `/api/v1` 管理。发生不兼容变更时新增 `/api/v2` 并按发布计划保留旧版本窗口。

## 10. 联调优先级

1. **基线一，先完成**：手机号登录、推荐流、播放凭证、点赞/收藏、评论、视频上传到审核发布，建立 App/Admin 的 OpenAPI 契约测试。
2. **基线二，随即完成**：移动端 Playwright 验证单实例播放、滑动切换、页面隐藏暂停、播放失败恢复、乐观更新回滚。
3. 之后接入企微 OAuth/扫码、站内转发、举报与处置、配置与审计。
4. 每个异步接口返回任务 ID 时都要补齐任务查询、失败原因和重试权限，不能把部分失败包装为整体成功。
