# 映流后台分类与标签真实接口对接 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `yingliu-b` 的分类与标签管理默认连接现有 Spring Boot CRUD 接口，并具备稳定的错误反馈和路由切换行为。

**Architecture:** 保留页面依赖 `TaxonomyRepository` 的现有边界，只增强 HTTP adapter，并把分类标签的数据源开关从全局 Dashboard Mock 开关中拆出。分类和标签继续复用同一个页面，使用 `resource` 属性监听处理路由复用。

**Tech Stack:** Vue 3.5、TypeScript 5.6、Vue Router 4.5、Element Plus 2.8、Vitest 3、Spring Boot 3.3 API

## Global Constraints

- 默认使用真实分类标签接口；仅当 `VITE_USE_TAXONOMY_MOCK === 'true'` 时使用 Mock。
- 不修改工作台和视频管理的数据源选择。
- 不实现标签合并。
- 不调整现有页面视觉。
- 开发环境继续通过 `/api` 代理到 `http://localhost:8080`。
- 目标项目是 `D:\PrivateSoftware\gitClaude\yingliu-b`；计划文档保存在当前规格仓库。
- 当前目录不是 Git 仓库，所有计划中的提交步骤改为记录验证结果，不执行 `git commit`。
- 使用 Ponytail full：复用现有 repository、页面与环境变量机制，不新增依赖、工厂层或通用请求框架；测试只覆盖本次非平凡分支和真实接口契约。

---

### Task 1: 加固分类标签 HTTP Repository

**Files:**
- Create: `D:\PrivateSoftware\gitClaude\yingliu-b\src\features\taxonomy\repository.spec.ts`
- Modify: `D:\PrivateSoftware\gitClaude\yingliu-b\src\features\taxonomy\repository.ts`

**Interfaces:**
- Consumes: `TaxonomyRepository`、`CategoryInput`、`TagInput`、`PageQuery`
- Produces: `createHttpTaxonomyRepository(baseUrl?, fetcher?)` 和包含 `code`、`traceId`、`status`、`cause` 的 `TaxonomyError`

- [ ] **Step 1: 编写失败测试，锁定真实接口请求契约**

```ts
it('maps category list query to the admin API', async () => {
  const fetcher = vi.fn().mockResolvedValue(ok({ items: [], page: 2, pageSize: 20, total: 0 }));
  const repository = createHttpTaxonomyRepository('', fetcher);
  await repository.listCategories({ page: 2, pageSize: 20, keyword: '安全', status: 'ENABLED' });
  expect(fetcher).toHaveBeenCalledWith(
    '/api/v1/admin/categories?page=2&pageSize=20&keyword=%E5%AE%89%E5%85%A8&status=ENABLED',
    expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) })
  );
});
```

同一测试文件还要逐项断言分类/标签 `POST`、`PATCH`、`DELETE` 的 URL、method 和 JSON body。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/taxonomy/repository.spec.ts`

Expected: 新增的健壮错误处理断言失败，证明测试能够识别当前实现缺口。

- [ ] **Step 3: 增加业务错误、非 JSON 和网络错误测试**

```ts
await expect(repository.createTag(input)).rejects.toMatchObject({
  name: 'TaxonomyError',
  code: 'RESOURCE_CONFLICT',
  traceId: 'trace-taxonomy-001',
  status: 409
});
```

非 JSON 响应应得到“服务返回格式异常”，网络拒绝应得到“无法连接分类标签服务”。

- [ ] **Step 4: 最小化实现稳定响应解析**

```ts
export class TaxonomyError extends Error {
  readonly code?: string;
  readonly traceId?: string;
  readonly status?: number;

  constructor(message: string, options: { code?: string; traceId?: string; status?: number; cause?: unknown } = {}) {
    super(message, { cause: options.cause });
    this.name = 'TaxonomyError';
    this.code = options.code;
    this.traceId = options.traceId;
    this.status = options.status;
  }
}
```

`requestData` 必须先捕获 fetch 网络错误，再安全解析 JSON，最后同时检查 HTTP 状态和业务 `code`。

- [ ] **Step 5: 运行 Repository 测试**

Run: `npm test -- src/features/taxonomy/repository.spec.ts`

Expected: 全部通过。

### Task 2: 默认启用真实数据源并修复资源路由切换

**Files:**
- Modify: `D:\PrivateSoftware\gitClaude\yingliu-b\src\main.ts`
- Modify: `D:\PrivateSoftware\gitClaude\yingliu-b\src\views\TaxonomyManagementView.vue`
- Create: `D:\PrivateSoftware\gitClaude\yingliu-b\src\views\TaxonomyManagementView.spec.ts`

**Interfaces:**
- Consumes: `VITE_USE_TAXONOMY_MOCK`、`taxonomyRepositoryKey`、页面 `resource: 'categories' | 'tags'`
- Produces: 默认 HTTP repository；资源属性变化时的 `resetAndLoad(resource)` 行为

- [ ] **Step 1: 编写路由资源变化的失败测试**

```ts
const wrapper = mount(TaxonomyManagementView, {
  props: { resource: 'categories' },
  global: { provide: { [taxonomyRepositoryKey as symbol]: repository } }
});
await flushPromises();
expect(repository.listCategories).toHaveBeenCalledOnce();
await wrapper.setProps({ resource: 'tags' });
await flushPromises();
expect(repository.listTags).toHaveBeenCalledOnce();
```

测试环境注册 Element Plus，并把 `AdminShell` stub 为渲染默认插槽的容器。

- [ ] **Step 2: 运行页面测试并确认失败**

Run: `npm test -- src/views/TaxonomyManagementView.spec.ts`

Expected: `resource` 变化后 `listTags` 未调用。

- [ ] **Step 3: 独立分类标签 Mock 开关**

```ts
const useTaxonomyMock = import.meta.env.VITE_USE_TAXONOMY_MOCK === 'true';
const taxonomyRepository = useTaxonomyMock
  ? createMockTaxonomyRepository()
  : createHttpTaxonomyRepository(apiBaseUrl);
```

保留原有 `useMockApi` 对工作台和视频管理的控制，不更改两者行为。

- [ ] **Step 4: 监听 `resource` 并重置页面状态**

```ts
watch(() => props.resource, () => {
  editorOpen.value = false;
  editing.value = undefined;
  query.keyword = '';
  query.status = undefined;
  query.page = 1;
  items.value = [];
  void load();
});
```

- [ ] **Step 5: 运行页面与 Repository 测试**

Run: `npm test -- src/features/taxonomy/repository.spec.ts src/views/TaxonomyManagementView.spec.ts`

Expected: 全部通过。

### Task 3: 工程验证与真实接口冒烟检查

**Files:**
- Modify only if verification exposes a defect in files from Tasks 1-2.

**Interfaces:**
- Consumes: 完成后的 `yingliu-b` 与现有 Spring Boot 分类标签接口
- Produces: 可构建前端和可复现的 CRUD 验证结果

- [ ] **Step 1: 运行完整前端测试**

Run: `npm test`

Expected: 全部通过。

- [ ] **Step 2: 运行类型检查**

Run: `npm run typecheck`

Expected: 退出码 0。

- [ ] **Step 3: 运行生产构建**

Run: `npm run build`

Expected: 退出码 0，并生成 `dist`。

- [ ] **Step 4: 运行后端分类标签集成测试**

Run: `./mvnw -pl yingliu-api-app -am -Dtest=AdminContentIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: Docker 可用时测试通过；Docker 不可用时由 Testcontainers 标记跳过并记录环境限制。

- [ ] **Step 5: 真实接口冒烟验证**

在 MySQL 和 Spring Boot 已运行时，使用分类与标签页面执行：列表筛选 → 新增 → 编辑 → 停用/启用 → 删除。确认浏览器网络请求均命中 `/api/v1/admin/categories` 或 `/api/v1/admin/tags`，且没有标签合并请求。

- [ ] **Step 6: 记录最终验证结果**

汇总测试数量、类型检查、构建、后端集成测试和无法执行的外部环境检查；由于目录无 Git 元数据，不创建提交。
