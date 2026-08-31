# 映流后台 A02 运营工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可运行、可测试的映流后台应用壳和 A02 运营工作台，以 Semi Design 的视觉密度、TDesign 的页面结构和 Element Plus 的组件能力实现。

**Architecture:** Vue 3 页面按 `layouts`、`features/dashboard` 和 `views` 分离。页面通过可注入的 `DashboardRepository` 获取聚合快照，开发环境使用固定 mock，真实 HTTP repository 保留四个工作台端点的转换边界。通用壳不读取业务数据，工作台组件只消费 props。

**Tech Stack:** Vue 3.5、TypeScript 5.6、Vite 6、Vue Router 4、Element Plus 2.8、ECharts 5、Vitest 3、Vue Test Utils 2。

## Global Constraints

- 主要验收视口为 1440×900，最低验收视口为 1280×720。
- 画布 `#F6F8FB`，正文 `#1F2937`，主操作 `#5B6CFF`，进度强调 `#20D9D2`，风险强调 `#FF5A62`。
- 不引入第二套 UI 组件库，不复制参考站点源码或品牌资产。
- 页面组件不得直接请求网络；所有数据通过 `DashboardRepository` 获取。
- 当前目录不是 Git 仓库，计划中的提交步骤改为测试通过后的实现检查点记录。

---

### Task 1: 工作台数据契约与 repository

**Files:**
- Create: `apps/yingliu-admin/src/features/dashboard/model.ts`
- Create: `apps/yingliu-admin/src/features/dashboard/repository.ts`
- Create: `apps/yingliu-admin/src/features/dashboard/mockRepository.ts`
- Create: `apps/yingliu-admin/src/features/dashboard/repository.spec.ts`

**Interfaces:**
- Produces: `DashboardSnapshot`、`DashboardRange`、`DashboardRepository.getSnapshot(range)`、`dashboardRepositoryKey`、`mockDashboardRepository`。
- Consumes: 浏览器 `fetch` 和 API 通用响应 `{ code, message, data, traceId }`。

- [ ] **Step 1: 写 repository 失败测试**

```ts
it('loads the PRD dashboard snapshot from the mock repository', async () => {
  const snapshot = await mockDashboardRepository.getSnapshot('7d');
  expect(snapshot.metrics.find((metric) => metric.key === 'plays')?.value).toBe('128,460');
  expect(snapshot.storage.usedPercent).toBe(72);
});
```

- [ ] **Step 2: 运行测试并确认因模块不存在而失败**

Run: `pnpm --filter @yingliu/admin test -- repository.spec.ts`

- [ ] **Step 3: 实现类型、mock 数据和 HTTP 转换边界**

`DashboardRepository` 的签名固定为：

```ts
export interface DashboardRepository {
  getSnapshot(range: DashboardRange): Promise<DashboardSnapshot>;
}
```

HTTP repository 并行读取 `/api/v1/admin/dashboard/summary`、`trends?range=${range}`、`todos` 和 `storage`；任何失败抛出包含 `traceId` 的 `DashboardLoadError`。

- [ ] **Step 4: 运行 repository 测试**

Run: `pnpm --filter @yingliu/admin test -- repository.spec.ts`
Expected: PASS。

- [ ] **Step 5: 记录实现检查点**

确认 model、mock 与 HTTP repository 没有 Vue 组件依赖。

### Task 2: 应用壳、路由与视觉 token

**Files:**
- Modify: `apps/yingliu-admin/package.json`
- Modify: `pnpm-lock.yaml`
- Modify: `apps/yingliu-admin/src/main.ts`
- Modify: `apps/yingliu-admin/src/App.vue`
- Modify: `apps/yingliu-admin/src/styles/index.css`
- Create: `apps/yingliu-admin/src/router.ts`
- Create: `apps/yingliu-admin/src/layouts/AdminShell.vue`
- Create: `apps/yingliu-admin/src/layouts/AdminSidebar.vue`
- Create: `apps/yingliu-admin/src/layouts/AdminTopbar.vue`
- Create: `apps/yingliu-admin/src/layouts/AdminShell.spec.ts`

**Interfaces:**
- Produces: `/dashboard` 路由、`AdminShell` 的默认内容插槽、`AdminSidebar` 的 `collapsed`/`toggle` 契约。
- Consumes: Element Plus、Vue Router、`@element-plus/icons-vue`。

- [ ] **Step 1: 安装明确依赖**

Run: `pnpm --filter @yingliu/admin add echarts@^5.6.0 @element-plus/icons-vue@^2.3.2`

- [ ] **Step 2: 写应用壳失败测试**

```ts
it('collapses the sidebar while preserving the active dashboard item', async () => {
  const wrapper = mount(AdminShell, { slots: { default: '<div>内容</div>' } });
  await wrapper.get('[data-testid="sidebar-toggle"]').trigger('click');
  expect(wrapper.get('[data-testid="admin-shell"]').classes()).toContain('is-collapsed');
  expect(wrapper.get('[aria-current="page"]').text()).toContain('工作台');
});
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `pnpm --filter @yingliu/admin test -- AdminShell.spec.ts`

- [ ] **Step 4: 实现路由、应用壳与全局 token**

`App.vue` 只渲染 `<RouterView />`。侧栏宽度使用 `--yl-sidebar-width: 224px`，折叠值为 `72px`；顶部栏高度使用 `--yl-topbar-height: 56px`。禁用菜单项点击时用 Element Plus 提示“即将开放”。

- [ ] **Step 5: 运行应用壳测试和类型检查**

Run: `pnpm --filter @yingliu/admin test -- AdminShell.spec.ts`
Run: `pnpm --filter @yingliu/admin typecheck`
Expected: PASS。

### Task 3: 工作台展示组件

**Files:**
- Create: `apps/yingliu-admin/src/features/dashboard/components/MetricCard.vue`
- Create: `apps/yingliu-admin/src/features/dashboard/components/TrendPanel.vue`
- Create: `apps/yingliu-admin/src/features/dashboard/components/TodoPanel.vue`
- Create: `apps/yingliu-admin/src/features/dashboard/components/StoragePanel.vue`
- Create: `apps/yingliu-admin/src/features/dashboard/components/RecentVideosTable.vue`
- Create: `apps/yingliu-admin/src/features/dashboard/components/DashboardComponents.spec.ts`

**Interfaces:**
- Consumes: Task 1 的 `DashboardSnapshot` 子类型。
- Produces: `TrendPanel` 事件 `update:range`，其他组件为纯 props 展示组件。

- [ ] **Step 1: 写组件行为测试**

```ts
it('emits the selected trend range', async () => {
  const wrapper = mount(TrendPanel, { props: { trend } });
  await wrapper.get('[data-range="30d"]').trigger('click');
  expect(wrapper.emitted('update:range')).toEqual([['30d']]);
});

it('shows upload protection at ninety percent', () => {
  const wrapper = mount(StoragePanel, { props: { storage: { ...storage, usedPercent: 91 } } });
  expect(wrapper.text()).toContain('已暂停新上传');
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `pnpm --filter @yingliu/admin test -- DashboardComponents.spec.ts`

- [ ] **Step 3: 实现组件**

TrendPanel 使用 ECharts 按需导入 `LineChart`、`GridComponent`、`TooltipComponent` 和 `CanvasRenderer`；组件卸载时调用 `dispose()`，ResizeObserver 触发 `resize()`。状态标签同时包含文本和色点。

- [ ] **Step 4: 运行组件测试**

Run: `pnpm --filter @yingliu/admin test -- DashboardComponents.spec.ts`
Expected: PASS。

### Task 4: DashboardView 组合与状态处理

**Files:**
- Create: `apps/yingliu-admin/src/views/DashboardView.vue`
- Create: `apps/yingliu-admin/src/views/DashboardView.spec.ts`
- Delete: `apps/yingliu-admin/src/views/HealthView.vue`
- Delete: `apps/yingliu-admin/src/views/HealthView.spec.ts`

**Interfaces:**
- Consumes: `DashboardRepository` 注入、Task 2 的 `AdminShell`、Task 3 的展示组件。
- Produces: 加载骨架、成功工作台、错误条、重试动作和 7/30 日数据重新加载。

- [ ] **Step 1: 写页面成功与失败测试**

```ts
it('renders the dashboard snapshot', async () => {
  const wrapper = mountDashboard(mockDashboardRepository);
  await flushPromises();
  expect(wrapper.text()).toContain('128,460');
  expect(wrapper.text()).toContain('客户现场安全操作要点');
});

it('retries after a repository failure', async () => {
  const repository = createFailOnceRepository();
  const wrapper = mountDashboard(repository);
  await flushPromises();
  await wrapper.get('[data-testid="dashboard-retry"]').trigger('click');
  await flushPromises();
  expect(wrapper.text()).toContain('128,460');
});
```

- [ ] **Step 2: 运行页面测试并确认失败**

Run: `pnpm --filter @yingliu/admin test -- DashboardView.spec.ts`

- [ ] **Step 3: 实现页面组合**

页面首次加载显示同形骨架；失败时保留 AdminShell，显示“工作台数据加载失败，请检查服务后重试”和可用 traceId；重试按钮重新请求当前范围。

- [ ] **Step 4: 运行全部后台测试**

Run: `pnpm --filter @yingliu/admin test`
Expected: PASS，旧 HealthView 测试已由 DashboardView 测试替代。

### Task 5: 构建与浏览器验收

**Files:**
- Modify only if QA reveals defects in Task 2-4 files.

**Interfaces:**
- Consumes: 完整后台前端。
- Produces: 通过构建、类型检查和双视口视觉验收的首个切片。

- [ ] **Step 1: 运行静态验证**

Run: `pnpm --filter @yingliu/admin typecheck`
Run: `pnpm --filter @yingliu/admin build`
Expected: 两条命令均退出码 0。

- [ ] **Step 2: 启动开发服务器**

Run: `pnpm --filter @yingliu/admin dev --host 127.0.0.1`

- [ ] **Step 3: 浏览器验证 1440×900**

检查应用壳、四项指标、趋势、待办、存储、最近视频、侧栏折叠和 7/30 日切换；页面不得横向滚动。

- [ ] **Step 4: 浏览器验证 1280×720**

检查侧栏自动收窄、图表尺寸、表格容器滚动和键盘焦点；主内容不得裁切。

- [ ] **Step 5: 修复 QA 差异并复跑全部验证**

Run: `pnpm --filter @yingliu/admin test && pnpm --filter @yingliu/admin typecheck && pnpm --filter @yingliu/admin build`
Expected: 全部通过。
