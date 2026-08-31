# 分类管理导航名称修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让分类管理页面在侧栏中显示并高亮“分类管理”，不再显示“系统与存储”。

**Architecture:** 保留现有路由数组和 `route.path === item.to` 激活逻辑，只修正 `/system/categories` 的展示名称。用一个侧栏组件测试锁定分类路由与当前项文本。

**Tech Stack:** Vue 3.5、Vue Router 4.5、Element Plus 2.8、Vitest 3

## Global Constraints

- 使用 Ponytail full，只修改现有导航项和一项回归测试。
- 不新增依赖，不创建父级菜单，不修改路由和图标。
- “标签管理”入口保持不变。
- 目标项目 `D:\PrivateSoftware\gitClaude\yingliu-b` 没有 Git 元数据，不执行提交。

---

### Task 1: 修正分类导航名称

**Files:**
- Modify: `D:\PrivateSoftware\gitClaude\yingliu-b\src\layouts\AdminSidebar.vue`
- Create: `D:\PrivateSoftware\gitClaude\yingliu-b\src\layouts\AdminSidebar.spec.ts`

**Interfaces:**
- Consumes: `useRoute().path` 和 `navigation` 数组
- Produces: `/system/categories` 对应文本“分类管理”及唯一 `aria-current="page"`

- [ ] **Step 1: 编写失败测试**

```ts
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/system/categories' }) }));

expect(wrapper.get('[aria-current="page"]').text()).toContain('分类管理');
expect(wrapper.text()).not.toContain('系统与存储');
```

- [ ] **Step 2: 运行测试并确认因旧名称失败**

Run: `npm test -- src/layouts/AdminSidebar.spec.ts`

Expected: 当前项文本为“系统与存储”，断言失败。

- [ ] **Step 3: 最小修改导航项**

```ts
{ label: '分类管理', icon: Setting, to: '/system/categories' }
```

- [ ] **Step 4: 运行测试与工程检查**

Run: `npm test`

Run: `npm run typecheck`

Run: `npm run build`

Expected: 全部退出码为 0。

- [ ] **Step 5: 浏览器验证**

重新加载 `http://127.0.0.1:5173/admin/system/categories`，确认页面和当前侧栏项均显示“分类管理”；再进入 `/admin/system/tags`，确认“标签管理”高亮。检查控制台无相关错误并截取修复后截图。
