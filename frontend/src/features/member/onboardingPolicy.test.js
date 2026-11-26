import { test } from "node:test";
import assert from "node:assert/strict";
import { canApplyOrganization, canJoinOrganization, canEnrollResident, eligibleBuildingOrganizations, canSeeNavigation, canUseOrganizationFeature, onboardingIntent, onboardingQuery, onboardingDestination } from "./onboardingPolicy.js";

const user=(...roles)=>({id:1,platformAdmin:false,emailVerified:true,memberships:roles.map(role=>({role}))});
test("only verified unaffiliated members or existing organization admins may apply",()=>{
  assert.equal(canApplyOrganization(user()),true);
  assert.equal(canApplyOrganization(user("USER")),false);
  assert.equal(canApplyOrganization(user("TECHNICIAN")),false);
  assert.equal(canApplyOrganization(user("USER","TECHNICIAN")),false);
  assert.equal(canApplyOrganization(user("USER","ADMIN")),true);
  assert.equal(canApplyOrganization({...user(),platformAdmin:true}),false);
  assert.equal(canApplyOrganization({...user(),emailVerified:false}),false);
});
test("onboarding intent is a closed allowlist, not an open redirect or role grant",()=>{
  assert.equal(onboardingIntent("?onboarding=invitation"),"invitation");
  assert.equal(onboardingIntent("?onboarding=https://example.com"),"");
  assert.equal(onboardingIntent("?onboarding=constructor"),"");
  assert.equal(onboardingQuery("resident"),"?onboarding=resident");
  assert.equal(onboardingDestination(user("USER"),"organization"),"/organizations/join");
  assert.equal(onboardingDestination(user(),"organization"),"/organizations/apply");
  assert.equal(onboardingDestination({...user(),emailVerified:false},"invitation"),"/email-verification?onboarding=invitation");
  assert.equal(onboardingDestination(user("TECHNICIAN"),"invitation"),"/organizations/join");
  assert.equal(onboardingDestination(user(),"https://example.com"),"/member");
});

test("unknown claims and platform accounts fail closed",()=>{
 for(const u of [{...user(),memberships:undefined},user("UNKNOWN"),{...user(),platformAdmin:true},{...user(),platformAdmin:undefined}]){
  assert.equal(canApplyOrganization(u),false);assert.equal(canJoinOrganization(u),false);assert.equal(canEnrollResident(u),false);
 }
});
test("building applicant exception is account bound",()=>{
 const pending={id:9,applicantId:1,kind:"BUILDING",status:"PENDING_APPROVAL"};
 for(const role of ["USER","TECHNICIAN"]){
  assert.equal(eligibleBuildingOrganizations(user(role)).size,0);
  assert.equal(eligibleBuildingOrganizations(user(role),[pending]).size,1);
  assert.equal(eligibleBuildingOrganizations(user(role),[{...pending,applicantId:2}]).size,0);
  assert.equal(eligibleBuildingOrganizations(user(role),[{...pending,status:"REJECTED"}]).size,0);
 }
});
test("sidebar and scoped permissions match roles and organization kinds",()=>{
 for(const role of ["USER","TECHNICIAN","ADMIN"]){
  const u={...user(),memberships:[{role,organizationId:7,organizationKind:"BUILDING"}]};
  const s={activeRole:role,activeOrganizationId:7,activeOrganizationKind:"BUILDING"};
  assert.equal(canSeeNavigation(u,{url:"/complexes/manage",buildingManagement:true},s,[]),role==="ADMIN");
  assert.equal(canSeeNavigation(u,{url:"/residents/enroll",resident:true},s,[]),role==="USER");
  assert.equal(canUseOrganizationFeature(u,s,{roles:["ADMIN"],buildingOnly:true}),role==="ADMIN");
  assert.equal(canUseOrganizationFeature(u,{...s,activeOrganizationId:8},{roles:["ADMIN"]}),false);
 }
 const factory={...user(),memberships:[{role:"ADMIN",organizationId:7,organizationKind:"FACTORY"}]};
 assert.equal(eligibleBuildingOrganizations(factory).size,0);
 assert.equal(canUseOrganizationFeature(factory,{activeRole:"ADMIN",activeOrganizationId:7,activeOrganizationKind:"FACTORY"},{roles:["ADMIN"],buildingOnly:true}),false);
});
