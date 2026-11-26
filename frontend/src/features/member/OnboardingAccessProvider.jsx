import {createContext,useContext,useEffect,useState} from "react";
import {useLocation} from "react-router-dom";
import {useAuth} from "./AuthProvider";
import {myOrgs} from "./memberApi";
import {verifiedRoles,eligibleBuildingOrganizations} from "./onboardingPolicy";
import {explainError} from "../../api/errors";
const Context=createContext(null);
export function OnboardingAccessProvider({children}) {
  const {user}=useAuth();
  const {pathname,search}=useLocation();
  const [revision,setRevision]=useState(0),[result,setResult]=useState(null);
  const key=verifiedRoles(user)!==null && user?.id!=null ? JSON.stringify([user.id,pathname,search,revision,user.memberships]) : null;
  useEffect(()=>{
    if(!key)return;
    const controller=new AbortController();
    myOrgs(controller.signal).then(items=>{
      if(controller.signal.aborted)return;
      if(!Array.isArray(items))throw new Error("INVALID_RESPONSE");
      setResult({key,items,error:""});
    }).catch(e=>{if(!controller.signal.aborted)setResult({key,items:[],error:explainError(e)});});
    return ()=>controller.abort();
  },[key]);
  const current=key && result?.key===key ? result : null;
  const applications=current?.items??[];
  return <Context.Provider value={{applications,eligible:eligibleBuildingOrganizations(user,applications),
    loading:Boolean(key&&!current),error:current?.error??"",reload:()=>setRevision(v=>v+1)}}>{children}</Context.Provider>;
}
export function useOnboardingAccess(){
  const ctx=useContext(Context);
  if(!ctx)throw new Error("OnboardingAccessProvider required");
  return ctx;
}
