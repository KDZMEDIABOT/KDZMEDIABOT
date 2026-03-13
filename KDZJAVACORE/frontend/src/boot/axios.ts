import { boot } from 'quasar/wrappers';
import axios, { AxiosInstance } from 'axios';

const TOKEN_KEY = 'aisystem_bearer_token';

declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    $axios: AxiosInstance;
  }
}

const api = axios.create({ baseURL: process.env.API_URL || 'http://localhost:8081/api' });

api.interceptors.request.use((config) => {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (token) config.headers.Authorization = `Bearer ${token}`;
  } catch { /* ignore */ }
  return config;
});

export default boot(({ app }) => {
  app.config.globalProperties.$axios = axios;
  app.config.globalProperties.$api = api;
});

export { api };
