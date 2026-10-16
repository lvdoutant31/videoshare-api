# 作者主页的关注状态

`GET /api/v1/app/users/{userId}` 在原公开资料与统计字段之外，返回布尔字段 `following`。
其含义是当前访问者是否关注此作者，不是作者关注了多少人。
已登录访问者的身份从 App 会话取得；游客返回 `false`。

此前接口未返回该字段，C 端按默认值 `false` 展示，导致已经关注后重新进入作者主页仍显示“关注”。
现在查询现有 `user_follow` 关系，每次进入或刷新主页都返回访问者自己的实际状态。
取消关注后再次查询返回 `false`，其他访问者不会继承别人的状态。

本次未新增数据库迁移或修改关注关系写入流程。
`AppApiIntegrationTest.followUpdatesCountsAndLists` 覆盖关注前、关注后重复访问、游客、其他登录用户、取消关注。
C 端 `AuthorProfileView.spec.ts` 覆盖返回 `following = true` 时显示“已关注”。

使用 `deploy/build-release.ps1` 生成 Windows 版本包，按 `deploy/README.md` 安装。
本版本同时包含此前的举报原因接口及其迁移；尚未安装该迁移的服务器，启动新版本前应先备份数据库。
保留现有运行配置和媒体目录，从新版本解压目录以管理员身份运行 `deploy-windows-service.bat deploy`。
