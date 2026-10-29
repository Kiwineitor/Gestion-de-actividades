import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { EN_APK } from './config.js';
import { observarTeclado } from './ui/teclado.js';
import { instalarRegistroErrores } from './ui/registroErrores.js';
import './style.css';

if (EN_APK) document.documentElement.classList.add('apk');

observarTeclado();

const app = createApp(App);
instalarRegistroErrores(app, router);
app.use(createPinia()).use(router).mount('#app');
