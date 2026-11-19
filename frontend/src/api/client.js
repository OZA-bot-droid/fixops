import axios from 'axios';
const api=axios.create({baseURL:'/api',timeout:10000});
let accessToken=null;
export const setAccessToken=(token)=>{accessToken=token};
api.interceptors.request.use(config=>{if(accessToken)config.headers.Authorization=`Bearer ${accessToken}`;return config;});
export default api;
export const unwrap=(response)=>response.data.data;
