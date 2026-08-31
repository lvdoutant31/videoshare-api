# 映流后台 A02 运营工作台设计规格

## 1. 目标与交付边界

本切片交付一个可运行、可测试、可继续扩展的 PC 管理后台基础：后台应用壳、一级导航、A02 运营工作台和服务异常反馈。它用于建立后续视频管理、审核、用户、评论、举报和系统配置页面共同遵循的视觉与工程基线。

本切片不实现真实登录、完整 RBAC、视频列表、审核处理、用户处置或系统配置，也不修改 H5。工作台先通过单一数据访问接口读取数据；后端工作台 API 未完成时由前端 mock adapter 返回 PRD 中的示例数据，后续替换 adapter 时不改页面组件。

## 2. 参考基线

- 视觉密度与状态表达以 Semi Design 的中后台范式为主要参考：克制的容器、紧凑但可读的表格、明确的状态色和操作层级。
- 页面结构以 TDesign Starter 的工作台、列表和详情页组织方式为参考：固定应用壳、左侧一级导航、顶部上下文区、主体模块化布局。
- 实现继续使用仓库既有 Vue 3、TypeScript、Vite 和 Element Plus，不引入第二套组件库。参考站点只提供布局和视觉判断，不复制其源码、图标资产或品牌元素。
- 视觉语言遵循现有“明场运营”定义：画布 `#F6F8FB`，正文 `#1F2937`，主操作 `#5B6CFF`，实时或进度强调 `#20D9D2`，风险强调 `#FF5A62`。

## 3. 信息架构

后台根路由进入 `/dashboard`。应用壳由三层构成：

1. 左侧主导航：映流品牌、工作台、视频管理、审核管理、用户管理、评论与举报、系统与存储。当前切片只有工作台可进入；其余入口显示为禁用状态并带“即将开放”提示，不创建空白业务页面。
2. 顶部栏：当前页面标题、数据更新时间、服务状态、通知入口和当前管理员摘要。
3. 内容区：工作台摘要、趋势、待办、存储状态与最近视频。

工作台主体按以下顺序展示：

- 第一行：今日播放、今日新增用户、待审核视频、播放失败率四项指标。
- 第二行：最近 7/30 日播放与互动趋势；待办事项队列。
- 第三行：本地媒体存储状态；最近视频表格。

1440×900 为主要验收视口，1280×720 为最低验收视口。低于 1366 像素时侧栏收窄为图标导航；内容区不得出现横向页面滚动，表格自身可在容器内滚动。

## 4. 组件边界

- `AdminShell`：只负责整体布局、导航展开状态和内容插槽，不请求业务数据。
- `AdminSidebar`：渲染导航定义、选中状态、禁用提示与折叠切换。
- `AdminTopbar`：渲染页面上下文、更新时间、连接状态和管理员摘要。
- `MetricCard`：渲染指标名称、权威值、变化说明和语义状态。
- `TrendPanel`：渲染 7/30 日切换和播放、互动两条趋势；图表使用 ECharts 的按需模块。
- `TodoPanel`：渲染待审核、举报、转码失败和容量告警，并提供明确动作标签。
- `StoragePanel`：展示 `/media` 使用率、备份状态和 80%/90% 阈值规则。
- `RecentVideosTable`：展示标题、作者、状态、播放量、发布时间和下一步动作。
- `DashboardView`：组合上述模块，管理加载、成功和失败状态，不包含通用布局细节。

每个组件仅消费显式 props 并发出语义事件。页面不得从子组件内部直接访问网络，也不得把所有工作台内容写进一个单体 Vue 文件。

## 5. 数据契约与状态

前端定义 `DashboardSnapshot`：

```ts
export interface DashboardSnapshot {
  updatedAt: string;
  metrics: Array<{
    key: 'plays' | 'newUsers' | 'pendingReviews' | 'playFailureRate';
    label: string;
    value: string;
    changeLabel: string;
    tone: 'positive' | 'neutral' | 'warning' | 'danger';
  }>;
  trend: {
    range: '7d' | '30d';
    points: Array<{ date: string; plays: number; interactions: number }>;
  };
  todos: Array<{
    key: 'videoReview' | 'report' | 'transcodeFailure' | 'storageWarning';
    label: string;
    count: number;
    tone: 'neutral' | 'warning' | 'danger';
  }>;
  storage: {
    mount: '/media';
    usedPercent: number;
    backupStatus: 'healthy' | 'delayed' | 'failed';
    lastBackupAt: string;
  };
  recentVideos: Array<{
    id: string;
    title: string;
    author: string;
    status: 'published' | 'pendingReview' | 'transcoding' | 'transcodeFailed' | 'offShelf';
    playCount: number | null;
    publishedAt: string | null;
  }>;
}
```

数据通过 `DashboardRepository.getSnapshot(range)` 获取。默认 repository 先调用 `/api/v1/admin/dashboard/summary`、`trends`、`todos` 和 `storage`；在开发环境中可切换到固定 mock repository。mock 值使用 PRD 示例：今日播放 `128,460`、待审核 `18`、播放失败率 `0.7%`、本地存储使用率 `72%`。

## 6. 视觉规则

- 页面使用系统字体栈，中文优先 `Noto Sans SC`、`Microsoft YaHei`，数字和英文优先 `Inter`；不依赖运行时外部字体请求。
- 主侧栏宽 224 像素，收窄状态 72 像素；顶部栏高 56 像素；内容最大宽度不封顶，但左右留白随视口从 20 到 28 像素变化。
- 画布与内容表面分离，卡片圆角为 8 像素，边框优先于阴影；不使用大面积渐变、玻璃拟态或浮夸装饰。
- 指标卡保持同高，数字突出，解释文字不超过两行。颜色只表达动作、状态或实时进度，不把每张卡染成不同颜色。
- “流光进度线”只用于存储使用率、备份或异步任务进度，青色主线可带短珊瑚风险节点；它是品牌识别点，不扩散到普通分隔线。
- 表格采用紧凑密度，表头与正文具有稳定对齐；状态使用文字加色点或浅色标签，不能只依赖颜色。
- 所有按钮、菜单、图表切换和表格动作提供可见键盘焦点；尊重 `prefers-reduced-motion`。

## 7. 加载、错误与空状态

- 首次加载使用与最终布局同形的骨架，不显示全屏旋转加载器。
- API 失败时保留应用壳，在内容区顶部展示错误条：“工作台数据加载失败，请检查服务后重试”，附“重新加载”按钮和 traceId（存在时）。
- 单个模块失败时只替换对应模块，其他成功数据继续可用；本切片的 repository 应保留模块级错误结构。
- 最近视频为空时展示“暂无最近视频”，不伪造示例行；待办为空时展示“当前没有待处理事项”。
- 存储达到 80% 显示预警，达到 90% 显示“已暂停新上传”，但工作台其他功能保持可用。

## 8. 测试与验收

- Vitest + Vue Test Utils 验证指标、趋势范围切换、待办、存储阈值、空状态和失败重试。
- repository 测试验证响应转换、稳定错误码和 traceId 传播；页面测试通过注入 fake repository，不发真实网络请求。
- 运行 `pnpm --filter yingliu-b test`、`typecheck` 和 `build`。
- 浏览器验收覆盖 1440×900 与 1280×720：无内容裁切、无页面横向滚动、侧栏收窄正确、图表尺寸稳定、表格可读。
- 浏览器逐项验证：切换 7/30 日趋势、折叠侧栏、触发并重试加载失败、查看存储告警、使用键盘遍历主要交互。

## 9. 后续切片

后台后续按同一应用壳依次实现视频列表与上传、审核、用户、评论举报、系统存储。H5 独立进入下一次设计与实施循环：先读取 Figma 文件 `RjYiNGr6ojdIng117qtv33` 的具体页面和节点，再按画板逐屏实现，不从本后台规格推导手机端布局。
