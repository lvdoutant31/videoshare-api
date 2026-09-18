# 接口路径约定（避免同名重载）

背景：`AdminVideoController` 曾把两个语义不同的 POST 都放在 `/api/v1/admin/videos`，
仅靠 `consumes = multipart/form-data` 与 `consumes = application/json` 区分
（`PATCH /{videoId}` 同理）。这种"同路径同方法、Content-Type 分叉"的写法让接口
一眼看不出语义，也难以在网关/文档/日志里区分。整改后路径本身即语义。

## 规则

1. **一个路径 + 一个 HTTP 方法只允许一个接口。** 不同业务动作必须体现在路径
   或 HTTP 方法上；禁止仅靠 `consumes` / `produces` 区分两个 mapping。
2. **纯元数据 CRUD 走资源路径**：集合创建 `POST /videos`，实例更新
   `PATCH /videos/{id}`，实例删除 `DELETE /videos/{id}`。
3. **带文件或状态流转的复合动作挂在动作子路径上**（与本工程既有风格一致）：
   - 上传文件并创建 → `POST /videos/upload`
   - 上传新文件替换某实例 → `PATCH /videos/{id}/file`
   - 已有：`POST /videos/{id}/submit-review`、`/publish`、`/off-shelf`、
     `/restore`、`/batch/off-shelf` …
4. **路径末段用一眼可读的词**（upload、file、publish、off-shelf），不出现
   仅内部人才懂的缩写。
5. `consumes` / `produces` 保留并显式声明，但只作为参数格式约束（防御性），
   不承担路由区分职责。
6. 新建接口时若发现与既有 mapping 同路径同方法，说明语义重叠，应拆出新的
   子路径而不是继续叠加 `consumes`。

## 视频管理接口对照（整改后）

| 语义 | 方法与路径 | Content-Type |
|---|---|---|
| 分页列表 | `GET /api/v1/admin/videos` | – |
| 创建（引用已有媒体） | `POST /api/v1/admin/videos` | application/json |
| 上传文件并创建 | `POST /api/v1/admin/videos/upload` | multipart/form-data |
| 详情 | `GET /api/v1/admin/videos/{videoId}` | – |
| 更新元数据 | `PATCH /api/v1/admin/videos/{videoId}` | application/json |
| 上传新文件替换视频 | `PATCH /api/v1/admin/videos/{videoId}/file` | multipart/form-data |
| 删除 | `DELETE /api/v1/admin/videos/{videoId}` | application/json |
| 批量删除（软删除，原因必填） | `POST /api/v1/admin/videos/batch/delete` | application/json |
| 提交审核 / 发布 / 下架 / 批量下架 / 批量恢复等 | `POST /api/v1/admin/videos/{videoId}/submit-review`、`/batch/off-shelf` 等动作子路径 | 按需 |

## 自查清单（新增或修改接口时）

- [ ] 与现有所有 mapping 对比，`路径 + HTTP 方法` 无重复；
- [ ] 一眼能从路径读出"做什么"，不含纯 Content-Type 区分；
- [ ] 路径只含小写字母、数字、连字符；资源名词复数、动作词简短；
- [ ] `consumes`/`produces` 显式声明。
