# 内容管理与 C 端发布

内容继续使用原 `video` 记录和 `/videos` 读取接口，避免破坏既有收藏、评论、分享和审核流程。
`description` 现在保存富文本正文，视频媒体可为空；视频和有效正文至少有一项。
空白、空段落和不合法的图片地址不算有效正文，只有图片的正文允许发布。

## 图片与存储

- 图片只存在于正文 HTML 内，不另设上传字段或图片列表。图文封面取正文第一张图片，轮播按正文出现顺序。
- 视频和图片使用同一个 `MediaStorage`，由 `media.storage-type` 选择当前本地存储或已保留的 OSS 实现。
- 图片通过后台中转上传，支持 JPEG、PNG、GIF，单张最多 10 MB；视频沿用原上传限制及转码流程。
- B 端图片接口：`POST /api/v1/admin/content/images`，multipart 的 `file` 字段，要求创建或编辑权限。
- C 端图片接口：`POST /api/v1/app/content/images`，multipart 的 `file` 字段，要求登录且账号正常。
- 图片返回统一响应体，其 `data.url` 可直接插入正文。允许 HTTP(S) 和以单个 `/` 开头的本地地址。

## C 端发布

- 发布页 `/publish` 通过现有底部发布按钮进入，未登录时先跳转登录。
- `GET /api/v1/app/content/options` 返回可用分类及标签。
- `POST /api/v1/app/content` 接收 multipart：`title`、`description`、重复的 `categoryIds` / `tagIds`，以及可选 `file`。
- 作者从登录身份取得；新内容进入 `PENDING_REVIEW`，随后使用既有 B 端审核和发布流程。
- `/content/:contentId` 展示完整富文本正文；有视频的内容在混合流播放，标题可展开正文。
- 图文在混合流内左右切换图片、上下切换内容；文字内容使用前端默认封面。

## 图文播放量

公开详情页调用 `GET /api/v1/app/content/{contentId}`。无视频媒体的图文或纯文字内容，
每次成功打开（含刷新、再次进入）播放量加 1，游客和登录用户均计入；无需停留满 3 秒。
计数原子写入 `video_stats.play_count` 和 UTC 日期的 `video_watch_daily.effective_play_count`，
不增加观看时长或完播数，也不伪造视频播放会话。接口禁止缓存，返回更新后的播放量。
原 `/api/v1/app/videos/{videoId}` 只读取，列表、收藏、历史、分享卡片和作者预览不会触发此计数。
视频仍通过实际观看进度累计满 3 秒计一次播放。本调整无需新增数据库迁移。

## 数据升级

Flyway `V202610091200__support_mixed_content.sql` 将正文列扩展为 MEDIUMTEXT，允许视频媒体为空，
并把历史普通简介转成 HTML：转义文字中的 HTML 特殊字符，保留换行。
发布新版本时应让 Flyway 执行该新增迁移。既有迁移文件不需要修改，旧视频媒体引用保持不变。

## 验证

后端覆盖有效正文校验、安全 HTML、图片文件验证、无视频内容的管理及公开读取、C 端提交审核、禁言账号拒绝、历史简介迁移与图片上传的加密过滤规则。
前端覆盖旧播放器回归、图文详情、默认封面、多图顺序、鼠标与触摸方向区分、纯文字发布及视频加图片正文发布。

## Docker 部署

`compose.docker.yml` 用于在 Docker 中运行后端与已构建的 B / C 端静态页面。
容器通过 `host.docker.internal` 连接现有本机 MySQL 与 Redis，媒体目录继续挂载 `document/media`。
后端通过 Flyway 执行新增迁移；镜像包括 Java 17 与 FFmpeg，视频转码开启。

专用 MySQL 账号的凭据放在 `.env.docker`（已忽略，不提交），只授权访问映流数据库。
首次部署前，先使用 `mysqldump --single-transaction` 备份数据库至已忽略的 `output/backups`。

每次实施完成后，先运行对应测试并构建两个前端，再执行：

```powershell
.\mvnw.cmd -B -pl yingliu-admin -am package -DskipTests
docker compose -f compose.docker.yml up -d --build --wait
```

入口：B 端 `http://localhost:3000/videoshare/admin/`，C 端 `http://localhost:5173/videoshare/h5/`，
后端健康 `http://localhost:8080/health`；`8088` 继续提供媒体访问。
上线验证包括容器健康、Flyway 版本、已有视频读取、图片本地上传及 C 端提交审核。
本次已验证的直接访问地址也可使用 `127.0.0.1` 替代 `localhost`。

2026-10-09 部署验证：两个容器均 healthy，迁移版本为 `202610091200`。
真实 C 端多图提交、B 端正文审核预览和图片上传、已有视频 HTTP Range 读取均通过。
临时验证账号和内容已标记为删除，记录与验证图片保留。

### C 端认证入口

2026-10-09 已恢复手机号与密码登录及独立注册。注册填写真实姓名、手机号、密码和确认密码，
同意协议后调用现有 `/app/auth/register`，成功后返回登录页；登录调用 `/app/auth/password/login`。
登录成功优先回到原先访问的内部页面（如 `/publish`），默认进入 `/feed`。
忘记密码提示联系系统管理员。页面不显示短信验证码或企业微信登录入口，已有后端接口保留。
该调整已部署到 Docker，并通过真实注册、错误密码拒绝和登录回跳验证，无新增数据库迁移。
