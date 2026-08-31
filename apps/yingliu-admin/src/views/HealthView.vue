<script setup lang="ts">
import { ref } from 'vue';
import { getHealth } from '@yingliu/shared';

const state = ref<'idle' | 'ready' | 'unavailable'>('idle');
const service = ref('');

async function checkHealth() {
  try {
    const result = await getHealth(import.meta.env.VITE_API_BASE_URL ?? '', '/api/v1/admin/health');
    service.value = result.data.service;
    state.value = 'ready';
  } catch {
    state.value = 'unavailable';
  }
}
</script>

<template>
  <main class="workspace">
    <h1>映流运营后台</h1>
    <p>后台工程基线</p>
    <button type="button" @click="checkHealth">检查服务</button>
    <p v-if="state === 'ready'">{{ service }} 已就绪</p>
    <p v-else-if="state === 'unavailable'">服务暂不可用</p>
  </main>
</template>
