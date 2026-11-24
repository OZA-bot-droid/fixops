import {createContext,useCallback,useContext,useEffect,useMemo,useState} from 'react';
import * as memberApi from './memberApi';
import {setAccessToken} from '../../api/client';
const Context=createContext(null);
// Demo only: move refresh token into a secure HttpOnly cookie before production.
const KEY='fixops_demo_refresh';
export function AuthProvider({children}){
 const [user,setUser]=useState(null);
 const [loading,setLoading]=useState(true);
 const clearLocalAuth=useCallback(()=>{
  setAccessToken(null);sessionStorage.removeItem(KEY);setUser(null);
 },[]);
 const accept=useCallback((value)=>{
  setAccessToken(value.accessToken);
  sessionStorage.setItem(KEY,value.refreshToken);
  setUser(value.member);
 },[]);
 const signIn=useCallback(async(email,password)=>{
  const result=await memberApi.login({email,password});accept(result);
 },[accept]);
 const signOut=useCallback(async()=>{
  const token=sessionStorage.getItem(KEY);
  try {if(token)await memberApi.logout(token);}
  finally {clearLocalAuth();}
 },[clearLocalAuth]);
 useEffect(()=>{
  const token=sessionStorage.getItem(KEY);
  if(!token){setLoading(false);return;}
  memberApi.refresh(token).then(accept).catch(clearLocalAuth).finally(()=>setLoading(false));
 },[accept,clearLocalAuth]);
 const value=useMemo(()=>({user,loading,signIn,signOut,loggedIn:!!user}),[user,loading,signIn,signOut]);
 return <Context.Provider value={value}>{children}</Context.Provider>;
}
export const useAuth=()=>{const v=useContext(Context);if(!v)throw new Error('AuthProvider required');return v};
