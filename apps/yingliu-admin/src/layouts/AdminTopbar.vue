<script setup lang="ts">
import { Bell, Connection } from '@element-plus/icons-vue';

defineProps<{ updatedAt?: string }>();

function formatUpdatedAt(value?: string) {
  if (!value) return '正在同步数据';
  return `数据更新于 ${new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Shanghai'
  }).format(new Date(value))}`;
}
</script>

<template>
  <header class="admin-topbar">
    <div>
      <h1>运营工作台</h1>
      <p>{{ formatUpdatedAt(updatedAt) }}</p>
    </div>
    <div class="topbar-actions">
      <span class="connection-state"><el-icon><Connection /></el-icon>服务正常</span>
      <button type="button" class="icon-button" aria-label="通知"><el-icon><Bell /></el-icon><i></i></button>
      <div class="operator"><span class="operator-avatar">吴</span><span class="operator-copy"><b>吴骏</b><small>超级管理员</small></span></div>
    </div>
  </header>
</template>

<style scoped>
.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--yl-topbar-height);
  padding: 0 26px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--yl-border);
  box-sizing: border-box;
}

h1 { margin: 0; color: var(--yl-text); font-size: 17px; font-weight: 720; letter-spacing: -0.02em; }
p { margin: 3px 0 0; color: var(--yl-text-muted); font-size: 11px; }
.topbar-actions { display: flex; align-items: center; gap: 15px; }
.connection-state { display: inline-flex; align-items: center; gap: 6px; color: #28866f; font-size: 11px; }
.connection-state .el-icon { font-size: 14px; }
.icon-button { position: relative; display:grid; place-items:center; width:34px; height:34px; padding:0; color:#566176; background:transparent; border:0; border-radius:7px; cursor:pointer; }
.icon-button:hover { background:#f1f3f8; }
.icon-button i { position:absolute; right:7px; top:7px; width:5px; height:5px; background:var(--yl-coral); border:2px solid #fff; border-radius:50%; }
.operator { display:flex; align-items:center; gap:9px; padding-left:14px; border-left:1px solid var(--yl-border); }
.operator-avatar { display:grid; place-items:center; width:30px; height:30px; color:#fff; background:#202b43; border-radius:8px; font-size:12px; font-weight:700; }
.operator-copy { display:grid; gap:2px; }
.operator-copy b { color:var(--yl-text); font-size:12px; font-weight:650; }
.operator-copy small { color:var(--yl-text-muted); font-size:9px; }
@media (max-width: 1280px) { .operator-copy { display:none; } }
</style>

