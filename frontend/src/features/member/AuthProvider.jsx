import {createContext,useCallback,useContext,useEffect,useMemo,useState} from 'react';
import * as memberApi from './memberApi';import {setAccessToken} from '../../api/client';
const Context=createContext(null);
// Demo-only sessionStorage refresh token. Production: migrate to HttpOnly Secure SameSite cookie + CSRF.
const KEY='fixops_demo_refresh';
export function AuthProvider({children}){
 const [user,setUser]=useState(null);const [loading,setLoading]=useState(true);
 const signOut=useCallback(()=>{setAccessToken(null);sessionStorage.removeItem(KEY);setUser(null)},[]);
 const accept=useCallback((v)=>{setAccessToken(v.accessToken);sessionStorage.setItem(KEY,v.refreshToken);setUser(v.member)},[]);
 const signIn=useCallback(async(email,password)=>{const v=await memberApi.login({email,password});accept(v)},[accept]);
 useEffect(()=>{const t=sessionStorage.getItem(KEY);if(!t){setLoading(false);return;}
  memberApi.refresh(t).then(accept).catch(signOut).finally(()=>setLoading(false));
 },[accept,signOut]);
 const value=useMemo(()=>({user,loading,signIn,signOut,loggedIn:!!user}),[user,loading,signIn,signOut]);
 return <Context.Provider value={value}>{children}</Context.Provider>;
}
export const useAuth=()=>{const v=useContext(Context);if(!v)throw new Error('AuthProvider required');return v};
