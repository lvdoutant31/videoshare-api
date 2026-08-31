import { createApp } from 'vue';
import Vant from 'vant';
import 'vant/lib/index.css';
import App from './App.vue';
import './styles/index.css';

createApp(App).use(Vant).mount('#app');
