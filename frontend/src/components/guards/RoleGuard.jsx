import {useAuth} from "../../features/member/AuthProvider";
import {verifiedRoles,canUseOrganizationFeature} from "../../features/member/onboardingPolicy";
import { Link, Outlet } from "react-router-dom";
import { useOrganization } from "../../features/member/OrganizationProvider";
import { Alert, Button, Card } from "../ui";
export default function RoleGuard({ roles, requireComplex = false, buildingOnly = false }) {
  const {user}=useAuth();
  const { activeOrganizationId, activeRole, activeOrganizationKind, activeComplexId, complexesLoading, complexesError, complexes, reloadComplexes } = useOrganization();
  if(verifiedRoles(user)===null)return <Card className="p-7"><Alert tone="error">현재 계정의 업무 접근 권한이 없습니다.</Alert></Card>;
  if (activeOrganizationId == null) return <Card className="grid gap-4 p-7"><Alert>업무 조직을 먼저 선택해 주세요. 소속 조직이 없다면 가입을 신청할 수 있습니다.</Alert><Link className="text-fixops-primary underline" to="/organizations/join">조직 가입·초대 수락</Link></Card>;
  if (!canUseOrganizationFeature(user,{activeOrganizationId,activeRole,activeOrganizationKind},{roles,buildingOnly})) return <Card className="grid gap-4 p-7"><Alert tone="error">선택한 조직에서는 해당 기능을 이용할 권한이 없습니다.</Alert><Link className="text-fixops-primary underline" to="/member">대시보드로 돌아가기</Link></Card>;
  if (requireComplex && !["BUILDING", "FACTORY"].includes(activeOrganizationKind)) return <Card className="p-7"><Alert tone="error">조직 유형을 확인할 수 없습니다. 회원 정보를 갱신해 주세요.</Alert></Card>;
  if (requireComplex && activeOrganizationKind === "BUILDING") {
    if (complexesLoading) return <Card className="p-7"><p role="status">접근 가능한 단지를 확인하고 있습니다…</p></Card>;
    if (complexesError) return <Card className="grid gap-4 p-7"><Alert tone="error">{complexesError}</Alert><Button onClick={reloadComplexes}>다시 조회</Button></Card>;
    if (activeComplexId == null) return <Card className="p-7"><Alert>{complexes.length ? "상단에서 업무 단지를 선택해 주세요." : "접근 가능한 단지가 없습니다. 조직 관리자에게 단지 권한을 문의해 주세요."}</Alert></Card>;
  }
  return <Outlet key={`${activeOrganizationId}:${activeRole}:${requireComplex ? activeComplexId ?? "none" : "organization"}`} />;
}
