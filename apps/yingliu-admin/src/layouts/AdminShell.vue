<script setup lang="ts">
import { ref } from 'vue';
import AdminSidebar from './AdminSidebar.vue';
import AdminTopbar from './AdminTopbar.vue';

defineProps<{ updatedAt?: string }>();

const collapsed = ref(false);
</script>

<template>
  <div
    class="admin-shell"
    :class="{ 'is-collapsed': collapsed }"
    data-testid="admin-shell"
  >
    <AdminSidebar :collapsed="collapsed" @toggle="collapsed = !collapsed" />
    <div class="admin-stage">
      <AdminTopbar :updated-at="updatedAt" />
      <main class="admin-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  --yl-sidebar-current: var(--yl-sidebar-width);
  display: grid;
  grid-template-columns: var(--yl-sidebar-current) minmax(0, 1fr);
  min-width: 1180px;
  min-height: 100vh;
  background: var(--yl-canvas);
  transition: grid-template-columns 180ms ease;
}

.admin-shell.is-collapsed { --yl-sidebar-current: var(--yl-sidebar-collapsed); }
.admin-stage { min-width: 0; }
.admin-content { padding: 22px 26px 30px; }

@media (max-width: 1365px) {
  .admin-shell:not(.is-collapsed) { --yl-sidebar-current: var(--yl-sidebar-collapsed); }
}

@media (prefers-reduced-motion: reduce) {
  .admin-shell { transition: none; }
}
</style>

