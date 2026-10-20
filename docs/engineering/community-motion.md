# 首页与详情页微交互

本轮采用 Vue Bits 的 Rubber Segment、Fade Content 和 Pulse Heart 的交互方向，按现有 Vue 3、Vant 图标及社区样式重新实现。未复制整套组件源码，不新增 Tailwind、GSAP、motion-v 或图标包依赖。

## 页面行为

- 首页的“内容 / 作者”、搜索的“综合 / 最新 / 最热”采用等宽分段按钮，底色在 220ms 内平滑移动。使用原生按钮支持点击、触摸、键盘及 aria-pressed；不加入拖动切换。选中值仍来自路由，重复选择当前按钮不会发请求。
- 热门卡片和首次显示的搜索结果淡入 200ms，不逐行延迟显示。刷新已有结果、追加下一页和 KeepAlive 详情返回不重播整个列表的入场效果，不修改分页、去重或滚动恢复逻辑。
- 图文和视频详情、播放流的点赞与收藏显示状态后，图标轻微脉冲 360ms，数字过渡 180ms。组件仅展示父组件传入的状态和计数，不自行增加计数，不发送请求。
- 图文详情仍等待接口确认后更新，保留请求锁和失败提示。播放流保留既有乐观更新、失败回滚和双击点赞行为；按钮阻止原生点击冒泡到播放控制区。
- 系统启用 prefers-reduced-motion 时直接显示最终状态。动画不决定内容可见性，不持续运行帧循环；在组件卸载或离开 KeepAlive 时结束或取消动画。

## 验证与发布

- 前端单元测试覆盖受控选择、KeepAlive 返回、动画取消、禁用按钮和减少动态效果。
- 浏览器用例在 390×844 与 1280×900 验证排序、键盘、分页、详情返回、点赞等待及失败处理、播放流互动与减少动态效果。保留原首页展示和排序回归测试。
- 发布使用 deploy/build-release.ps1 构建 B/C 前端及后端，生成 Windows 服务版 ZIP。本轮无数据库结构变更。
- 服务端按 deploy/README.md 从解压版本目录运行 deploy-windows-service.bat deploy，沿用现有运行配置、数据库及媒体目录。

## 参考

- https://vue-bits.dev/micro/rubber-segment
- https://vue-bits.dev/animations/fade-content
- https://vue-bits.dev/micro/pulse-heart
