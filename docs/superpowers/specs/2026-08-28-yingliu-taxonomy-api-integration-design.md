# 映流后台分类与标签真实接口对接设计

## 背景

`yingliu-b` 已完成分类管理和标签管理页面，并通过 `TaxonomyRepository` 隔离数据来源。当前 `main.ts` 使用 `VITE_USE_DASHBOARD_MOCK` 同时控制工作台、视频和分类标签三个模块，且默认启用 Mock，因此分类标签页面虽然已有 HTTP repository，运行时仍不会默认访问真实后端。

Java 服务端已经提供以下接口：

- `GET /api/v1/admin/categories`
- `POST /api/v1/admin/categories`
- `PATCH /api/v1/admin/categories/{categoryId}`
- `DELETE /api/v1/admin/categories/{categoryId}`
- `GET /api/v1/admin/tags`
- `POST /api/v1/admin/tags`
- `PATCH /api/v1/admin/tags/{tagId}`
- `DELETE /api/v1/admin/tags/{tagId}`

本次不实现标签合并，也不改动现有页面视觉。

## 目标

1. 分类和标签页面默认访问真实 Java 接口。
2. 工作台和视频管理的数据源选择保持不变。
3. 仍允许开发者显式启用分类标签 Mock，便于离线开发。
4. 列表、筛选、分页、新增、编辑、启停和删除均完成真实接口闭环。
5. 网络错误、非 JSON 响应和后端业务错误能够显示可操作的信息，并保留 `traceId`。

## 非目标

- 标签合并。
- 分类层级或树形分类。
- 登录、权限和令牌刷新机制。
- 工作台、视频管理的数据源切换。
- 分类标签页面的视觉重构。

## 方案

### 数据源选择

新增独立环境变量 `VITE_USE_TAXONOMY_MOCK`：

- 值为字符串 `true` 时使用 `createMockTaxonomyRepository()`。
- 未配置或配置为其他值时使用 `createHttpTaxonomyRepository(apiBaseUrl)`。

原有工作台和视频管理继续使用当前开关，避免本次改动扩大到其他模块。

### HTTP 契约

前端继续使用 `TaxonomyRepository` 作为页面唯一数据入口：

- 列表查询把 `page`、`pageSize`、可选 `keyword` 和可选 `status` 编码为查询参数。
- 创建请求使用 `POST` 和 JSON 请求体。
- 编辑及启停使用 `PATCH` 和 JSON 请求体。
- 删除使用 `DELETE`。
- 同源开发环境通过 Vite `/api` 代理访问 `http://localhost:8080`。
- 部署环境可通过 `VITE_API_BASE_URL` 指定服务端地址。

接口返回统一按以下信封解析：

```ts
interface ApiEnvelope<T> {
  code: string;
  message?: string;
  data: T;
  traceId?: string;
}
```

只有 HTTP 成功且 `code === 'OK'` 才返回 `data`。

### 错误处理

`TaxonomyError` 作为前端统一错误类型，携带可选的 `code`、`traceId` 和 HTTP 状态。处理规则如下：

1. 后端业务失败：优先显示信封中的 `message`，保留响应体或响应头中的 `traceId`。
2. 非 JSON 响应：转换为“服务返回格式异常”，避免 JSON 解析异常直接泄露给页面。
3. 网络失败：转换为“无法连接分类标签服务”，保留原始错误作为 `cause`。
4. 空成功响应：仅允许删除操作通过显式的无数据处理完成；其他操作仍要求合法信封。

页面保持现有行为：首次加载失败显示完整错误状态，刷新失败且已有数据时显示行内错误，写操作失败使用消息提示。

### 页面数据流

```text
TaxonomyManagementView
        │
        ▼
TaxonomyRepository
        │
        ├── GET 列表、筛选、分页
        ├── POST 新增
        ├── PATCH 编辑/启停
        └── DELETE 删除
        │
        ▼
Spring Boot /api/v1/admin/categories|tags
        │
        ▼
统一 ApiResponse → 页面刷新当前页
```

新增成功后回到第一页并刷新；编辑、启停后刷新当前页；删除当前页最后一条记录时回退一页后刷新。

分类和标签路由复用同一个页面组件。页面必须监听 `resource` 属性变化；从分类切换到标签或反向切换时，关闭未提交的编辑弹窗、清空筛选条件、回到第一页并加载目标资源，避免 Vue 复用组件后继续显示上一类数据。

## 测试与验收

### Repository 测试

覆盖以下行为：

- 分类和标签列表的查询参数正确。
- 创建、编辑、启停和删除的 URL、HTTP 方法及 JSON 请求体正确。
- 后端业务错误映射为 `TaxonomyError`，且包含 `code` 和 `traceId`。
- 非 JSON 响应和网络异常转换为稳定的用户提示。

### 页面测试

使用注入的测试 repository 验证：

- 路由资源变化后加载正确的分类或标签数据。
- 查询、分页和刷新调用正确方法。
- 新增、编辑、启停和删除成功后刷新列表。
- 写操作失败时不错误关闭编辑弹窗，并展示后端消息。

### 工程验证

- `npm test`
- `npm run typecheck`
- `npm run build`
- 在 Java 服务、MySQL 启动后，通过 Vite 代理手动验证分类和标签完整 CRUD。

## 完成标准

- 未配置 `VITE_USE_TAXONOMY_MOCK` 时，两个页面不再读取 Mock 数据。
- 工作台和视频管理不受分类标签数据源修改影响。
- 所有既定操作均命中真实接口并正确刷新页面。
- 后端的重复名称、资源被引用、校验失败等错误能够按原消息展示，并可提供诊断编号。
- 不包含标签合并相关接口或 UI。
