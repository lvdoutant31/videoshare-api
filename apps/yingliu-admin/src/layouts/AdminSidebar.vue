<script setup lang="ts">
import {
  ChatDotRound,
  Checked,
  DataBoard,
  Setting,
  User,
  VideoCamera
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { Component } from 'vue';

defineProps<{ collapsed: boolean }>();

const emit = defineEmits<{ toggle: [] }>();

interface NavigationItem {
  label: string;
  icon: Component;
  to?: string;
}

const navigation: NavigationItem[] = [
  { label: '工作台', icon: DataBoard, to: '/dashboard' },
  { label: '视频管理', icon: VideoCamera },
  { label: '审核管理', icon: Checked },
  { label: '用户管理', icon: User },
  { label: '评论与举报', icon: ChatDotRound },
  { label: '系统与存储', icon: Setting }
];

function showComingSoon(label: string) {
  ElMessage.info(`${label}即将开放`);
}
</script>

<template>
  <aside class="admin-sidebar" :aria-label="collapsed ? '主导航（已折叠）' : '主导航'">
    <div class="brand" aria-label="映流运营后台">
      <span class="brand-mark" aria-hidden="true"><i></i><i></i></span>
      <span v-show="!collapsed" class="brand-name">映流</span>
    </div>

    <nav class="sidebar-nav">
      <template v-for="item in navigation" :key="item.label">
        <RouterLink
          v-if="item.to"
          :to="item.to"
          class="nav-item nav-item--active"
          aria-current="page"
          :aria-label="collapsed ? item.label : undefined"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed">{{ item.label }}</span>
        </RouterLink>
        <button
          v-else
          type="button"
          class="nav-item nav-item--disabled"
          :aria-label="collapsed ? `${item.label}，即将开放` : undefined"
          @click="showComingSoon(item.label)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed">{{ item.label }}</span>
        </button>
      </template>
    </nav>

    <div class="sidebar-footer">
      <span class="service-dot" aria-hidden="true"></span>
      <span v-show="!collapsed">服务运行正常</span>
    </div>

    <button
      type="button"
      class="sidebar-toggle"
      data-testid="sidebar-toggle"
      :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
      @click="emit('toggle')"
    >
      <span aria-hidden="true">{{ collapsed ? '›' : '‹' }}</span>
    </button>
  </aside>
</template>

<style scoped>
.admin-sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  width: var(--yl-sidebar-current);
  min-height: 100vh;
  padding: 18px 12px 16px;
  color: #cbd5e1;
  background: #111827;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  transition: width 180ms ease;
  box-sizing: border-box;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 40px;
  padding: 0 10px 18px;
  color: #fff;
}

.brand-mark {
  display: grid;
  grid-template-columns: repeat(2, 7px);
  align-items: end;
  gap: 3px;
  width: 18px;
  height: 22px;
}

.brand-mark i {
  display: block;
  width: 7px;
  border-radius: 5px;
  transform: skewY(-18deg);
}

.brand-mark i:first-child { height: 14px; background: var(--yl-teal); }
.brand-mark i:last-child { height: 21px; background: var(--yl-indigo); }

.brand-name {
  font-size: 19px;
  font-weight: 780;
  letter-spacing: 0.12em;
}

.sidebar-nav { display: grid; gap: 5px; }

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 42px;
  padding: 0 12px;
  color: inherit;
  background: transparent;
  border: 0;
  border-radius: 7px;
  font: 500 13px/1 var(--yl-font-ui);
  text-decoration: none;
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
}

.nav-item .el-icon { flex: 0 0 auto; font-size: 18px; }

.nav-item--active {
  position: relative;
  color: #fff;
  background: #26324a;
}

.nav-item--active::before {
  position: absolute;
  left: 0;
  width: 3px;
  height: 18px;
  content: '';
  background: var(--yl-teal);
  border-radius: 0 3px 3px 0;
}

.nav-item--disabled { opacity: 0.72; }
.nav-item--disabled:hover { color: #fff; background: rgba(255, 255, 255, 0.05); }

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 12px;
  color: #8f9bad;
  font-size: 11px;
  white-space: nowrap;
}

.service-dot {
  width: 7px;
  height: 7px;
  background: #21c18c;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(33, 193, 140, 0.12);
}

.sidebar-toggle {
  position: absolute;
  right: -12px;
  top: 72px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: #667085;
  background: #fff;
  border: 1px solid #d9dee8;
  border-radius: 50%;
  font: 18px/1 Arial, sans-serif;
  cursor: pointer;
  box-shadow: 0 3px 8px rgba(20, 29, 47, 0.12);
}

.sidebar-toggle:focus-visible,
.nav-item:focus-visible { outline: 2px solid var(--yl-indigo); outline-offset: 2px; }
</style>
