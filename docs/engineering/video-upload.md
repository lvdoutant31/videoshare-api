# 视频上传流程（后端中转）

前端仅提交一次 file 和业务字段；后端通过 OSS SDK 上传，SDK 完成请求签名。业务上传不调用浏览器 PostObject 签名接口，也不要求 STS Role ARN。

接口路径即语义，不再按 Content-Type 区分接口：带文件上传使用 `POST /api/v1/admin/videos/upload`（multipart/form-data）；仅以 JSON 引用已有媒体创建草稿使用 `POST /api/v1/admin/videos`（application/json）；替换已有视频文件使用 `PATCH /api/v1/admin/videos/{videoId}/file`。

1. `POST /api/v1/admin/videos/upload`，使用 multipart/form-data 提交 file、authorId、title、description、sourceNote、categoryIds、tagIds、visibility。
2. 后端检查文件、大小、标题及分类，计算内容摘要；新媒体由 MediaStorage 存储。
3. OSS 模式由 OssStorageService 发起带 Callback 的 SDK PutObject 请求。
4. OSS 调用 `POST /api/v1/media/oss/callback`；后端验签并按对象 key 生成 URL，回给 OSS，再由 SDK 读取回调响应。此时尚未写入本次媒体和视频业务记录。
5. 上传用例验证回调 URL，与预期对象地址一致后，在同一数据库事务保存媒体信息（含 URL）和业务字段、分类/标签关联。
6. 原上传 HTTP 请求返回 201 / OK，视频 ID 及 `data.originalMedia.url`；前端用该字段预览。管理员创建沿用已有自动审核通过规则，上传成功不等于已发布。

重复内容复用已存在媒体记录；此分支不重新上传，也不触发新的 OSS 回调。上传/回调失败不进入业务入库事务；数据库步骤失败时回滚，并尝试清理本次新建的存储对象，共享对象不能删除。

## 配置

在实际运行环境设置 MEDIA_STORAGE_TYPE=oss、OSS_ENDPOINT、OSS_BUCKET_NAME、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET、OSS_CALLBACK_URL；如有资源域名则设置 OSS_URL_PREFIX。回调地址指向可被 OSS 访问的后端 `/api/v1/media/oss/callback`，媒体域名用于读取视频，两者用途不同。反向代理需保留验签使用的回调路径和原始请求体。访问地址是否可播放还取决于 Bucket 访问策略；当前代码按域名和对象 key 返回 URL，不生成私有对象的临时读取签名。

旧 `/api/get_post_signature_for_oss_upload`、`/api/v1/media/oss/upload` 是独立的 STS 工具入口，不承担视频业务字段入库；仅使用这些入口时要求 OSS_STS_ROLE_ARN。业务页面应使用上面的 `POST /api/v1/admin/videos/upload`。

管理端已映射 `originalMedia.url` 到播放器，并在 Vite 配置增加 `/media` 代理。本地模式用于开发验证；不能将本地成功记录为真实 OSS 联调成功。

## 本轮验证

- 后端全量测试 51 通过、0 失败、0 跳过，包含 Docker/MySQL 集成测试。
- OSS 存储测试模拟 SDK 回调，验证成功 URL、回调失败清理；上传用例验证 URL/字段入库，以及回调失败不进入数据库事务。
- 配置测试确认 SDK 中转可不填写 STS Role ARN，旧 STS 接口缺少 Role ARN 时在调用前报出配置错误。
- 管理端 `npm run build` 通过；浏览器详情页的本地视频能加载并播放。
- 真实 OSS 上传、域名连通、回调验签与私有资源权限仍需云端配置后验证，当前没有宣称实测通过。

本地 HTTP 补充验证：通过 3000 前端代理提交示例 flower.mp4 和业务字段，返回 201（测试视频 ID 2096513232788869121）；重新查询确认字段已保存，预览 URL 的 Range 请求返回 206 / 1024 bytes。该测试视频已软删除，媒体及审计历史保留。最新后端启动 PID 为 140984，端口 8080（PID 仅为本轮记录）。
