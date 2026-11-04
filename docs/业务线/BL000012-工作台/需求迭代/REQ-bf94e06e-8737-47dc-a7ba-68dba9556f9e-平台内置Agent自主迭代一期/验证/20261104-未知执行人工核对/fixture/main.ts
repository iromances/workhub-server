import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Harness from './Harness.vue'
createApp(Harness).use(createPinia()).use(ElementPlus).mount('#app')
