package com.fixops.member.web;

import com.fixops.member.service.OrganizationOnboardingService;
import com.fixops.member.service.VerificationDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class OrganizationOnboardingController {
    private final OrganizationOnboardingService service;
    private final VerificationDelivery delivery;
    public OrganizationOnboardingController(OrganizationOnboardingService service,VerificationDelivery delivery){this.service=service;this.delivery=delivery;}
    private long actor(JwtAuthenticationToken token){return Long.parseLong(token.getName());}
    public record Invite(@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(max=200) String name,@Pattern(regexp="BUILDING|FACTORY") @NotBlank String kind){}
    @PostMapping("/platform/organizations/onboarding-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> issue(@Valid @RequestBody Invite request,JwtAuthenticationToken token){
        var result=new LinkedHashMap<>(service.issue(actor(token),request.email(),request.name(),request.kind()));
        result.put("emailSent",delivery.notifyOrganizationInvitation(request.email().trim().toLowerCase(Locale.ROOT)));
        return Api.ok(result);
    }
    @GetMapping("/platform/organizations/onboarding-invitations")
    public Map<String,Object> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,JwtAuthenticationToken token){return Api.ok(service.list(actor(token),page,size));}
    @PatchMapping("/platform/organizations/onboarding-invitations/{id}/revoke")
    public Map<String,Object> revoke(@PathVariable long id,JwtAuthenticationToken token){service.revoke(actor(token),id);return Api.ok(Map.of("revoked",true));}
    @GetMapping("/organizations/onboarding-invitations/mine")
    public Map<String,Object> mine(JwtAuthenticationToken token){return Api.ok(service.mine(actor(token)));}
    @PostMapping("/organizations/onboarding-invitations/{id}/accept")
    public Map<String,Object> accept(@PathVariable long id,JwtAuthenticationToken token){return Api.ok(service.accept(actor(token),id));}
}
