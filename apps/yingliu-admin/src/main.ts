import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import { router } from './router';
import { dashboardRepositoryKey, createHttpDashboardRepository } from './features/dashboard/repository';
import { mockDashboardRepository } from './features/dashboard/mockRepository';
import './styles/index.css';

const dashboardRepository = import.meta.env.VITE_USE_DASHBOARD_MOCK === 'false'
  ? createHttpDashboardRepository(import.meta.env.VITE_API_BASE_URL ?? '')
  : mockDashboardRepository;

createApp(App)
  .use(ElementPlus)
  .use(router)
  .provide(dashboardRepositoryKey, dashboardRepository)
  .mount('#app');
