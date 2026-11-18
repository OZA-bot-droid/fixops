import axios from 'axios';
// Interceptors and token-refresh policy to be added with Member contract; no fake auth.
export const apiClient = axios.create({ baseURL: '/api', timeout: 10000 });
