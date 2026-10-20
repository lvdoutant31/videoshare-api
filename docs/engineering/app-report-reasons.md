# C 端举报原因接口

`GET /api/v1/app/report-reasons?targetType=VIDEO` 返回统一响应中的 `data` 数组。
`targetType` 支持 `VIDEO`、`COMMENT`、`USER`，非法值返回 422。
每项包含 `code`、`name`、`targetTypes`，与 C 端 `ReportReason` 契约一致。

原因来自现有 `sys_report_reason` 表，只返回 `status = ENABLED` 且
`target_types` 包含对应对象的记录，按 `sort`、`id` 排序。
查询不写入数据，也不在进程中缓存配置。

此前前端打开举报面板会请求此地址，但后端未注册 GET 路由，返回 404。
视频、评论、用户的 POST 举报路由已经存在，无需修改前端请求路径。

迁移 `V202610101200__seed_app_report_reasons.sql` 补入六个默认原因：
SPAM、PORNOGRAPHY、VIOLENCE、INFRINGEMENT、RUMOR、OTHER。
已有同编码记录保持原名称、状态、排序和适用对象，包括已经停用的记录。

## 验证与安装

`AppApiIntegrationTest` 在隔离 MySQL 中覆盖三类对象的默认原因、对象筛选、
停用原因过滤、排序、非法参数、初始化脚本保留已有配置，以及三类举报提交落库。
不要把该测试连接到已有业务库：其测试准备步骤会清理测试数据表。

按 `deploy/README.md` 构建 Windows 发布 ZIP。服务器安装前先备份现有数据库，
沿用原媒体目录和运行配置；在新版本解压目录以管理员身份执行
`deploy-windows-service.bat deploy`，由 Flyway 执行新增迁移。
Nginx 前端资源按既有流程更新。

安装后检查上述 GET 地址返回 200，打开视频或用户举报面板能显示原因；
登录后提交举报，再在 B 端举报管理确认记录。不要为了验收向真实业务对象发送测试举报。

## 举报查看与处置

B 端侧栏分别提供“评论管理”(`/comments`) 和“举报管理”(`/reports`)，按各自的
`comment:view`、`report:view` 权限显示。“评论与举报”旧入口只链接到评论页，
原页面没有举报切换入口，导致举报队列无法从菜单访问；独立入口修复这一问题。
现有管理员授权仍保留评论与举报的权限组合，后台接口继续独立校验举报权限。

C 端内容、评论（含回复）和用户举报均先选择对应对象的启用原因，可填写补充说明；
提交时发送 `reasonCode`、`description` 和原对象 ID。视频与图文共用 `VIDEO` 对象类型。

B 端 `GET /api/v1/admin/reports` 支持 `page`、`pageSize`、`status`、`targetType`、`riskLevel`。
列表和详情返回对象摘要、举报人、原因名称及处理人的 `assigneeAdminId`；
详情还返回对象快照、`resolutionNote` 和 `history`。旧记录缺少快照时展示当前对象信息与举报说明。

状态流转为待领取 `PENDING` → 处理中 `PROCESSING` → 已解决 `RESOLVED` 或已驳回 `REJECTED`。
旧数据库中的 `CLAIMED` 在查询和返回时兼容为 `PROCESSING`；旧版本将驳回存为
`status=RESOLVED,resolution=REJECTED` 的记录兼容为 `REJECTED`，无需迁移数据。

管理员通过 `POST /api/v1/admin/reports/{id}/claim` 领取。抢领失败返回 409；
仅当前领取人且具有对应操作权限时可以处置。
解决接口 `/resolve` 接收 `{"resolution":"RESOLVED","note":"处理原因"}`；
驳回接口 `/reject` 接收 `{"note":"驳回原因"}`。处理原因不能为空，最多 300 字。
领取、解决和驳回与审计记录在同一事务中提交，详情按时间展示处理记录。
旧 `/api/v1/admin/moderation/reports/{id}/resolve` 入口共用领取人校验。

解决或驳回只记录举报结论；管理员另行下架内容、隐藏评论或封禁用户，不自动执行处罚。
