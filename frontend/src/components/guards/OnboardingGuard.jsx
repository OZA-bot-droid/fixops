import {Link,Outlet} from "react-router-dom";
import {useAuth} from "../../features/member/AuthProvider";
import {useOnboardingAccess} from "../../features/member/OnboardingAccessProvider";
import {canApplyOrganization,canJoinOrganization,canEnrollResident} from "../../features/member/onboardingPolicy";
import {Alert,Button,Card} from "../ui";
export default function OnboardingGuard({feature}){
  const {user}=useAuth();
  const {eligible,loading,error,reload}=useOnboardingAccess();
  if(feature==="building"&&!eligible.size&&loading)return <Card className="p-7"><p role="status">단지 등록 권한 확인 중…</p></Card>;
  if(feature==="building"&&!eligible.size&&error)return <Card className="grid gap-4 p-7"><Alert tone="error">{error}</Alert><Button onClick={reload}>권한 다시 확인</Button></Card>;
  const allowed=feature==="building"?eligible.size>0:feature==="apply"?canApplyOrganization(user):feature==="join"?canJoinOrganization(user):feature==="resident"?canEnrollResident(user):false;
  return allowed?<Outlet/>:<Card className="grid gap-4 p-7"><Alert tone="error">현재 계정에는 이 기능을 이용할 권한이 없습니다.</Alert><Link className="text-fixops-primary underline" to="/member">대시보드로 돌아가기</Link></Card>;
}
