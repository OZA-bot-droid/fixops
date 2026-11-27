package com.fixops.member.web;

import com.fixops.member.service.ComplexService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class ComplexController {
    private final ComplexService service;
    private final com.fixops.member.service.KaptClient kapt;
    private final com.fixops.member.service.RequestRateLimiter limiter;
    private final com.fixops.member.service.KaptCatalogService catalog;
    public ComplexController(ComplexService service,com.fixops.member.service.KaptClient kapt,com.fixops.member.service.RequestRateLimiter limiter,com.fixops.member.service.KaptCatalogService catalog){this.service=service;this.kapt=kapt;this.limiter=limiter;this.catalog=catalog;}
    private long actor(JwtAuthenticationToken token){return Long.parseLong(token.getName());}
    public record Register(@Size(max=40) String kaptCode,@NotBlank @Size(max=200) String displayName,@NotBlank @Size(max=500) String roadAddress){}
    public record Decision(@NotNull Boolean approve,@NotBlank @Size(max=500) String reason,Boolean confirmDistinctAddress){}
    public record Access(@NotNull Boolean grant){}
    @GetMapping("/complexes/search")
    public Map<String,Object> search(@RequestParam(defaultValue="") String regionCode,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        limiter.check("kapt-global","all",120,60);
        limiter.check("kapt-member",token.getName(),20,60);
        return Api.ok(catalog.search(regionCode,q,page,size));
    }
    @GetMapping("/complexes/catalog/status")
    public Map<String,Object> catalogStatus(JwtAuthenticationToken token){
        limiter.check("kapt-status",token.getName(),20,60);
        return Api.ok(catalog.coverage());
    }
    @GetMapping("/organizations/{id}/complexes/{complexId}/building-register-hint")
    public Map<String,Object> buildingRegisterHint(@PathVariable long id,@PathVariable long complexId,JwtAuthenticationToken token){
        limiter.check("kapt-global","all",120,60);
        limiter.check("kapt-member",token.getName(),20,60);
        var complex=service.mine(actor(token),id).stream()
            .filter(row -> ((Number)row.get("id")).longValue()==complexId && "ADMIN".equals(row.get("access_scope")))
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED"));
        Object code=complex.get("kapt_code");
        if (!(code instanceof String kaptCode) || kaptCode.isBlank())
            return Api.ok(Map.of("available",false));
        var hint=kapt.parcelHint(kaptCode);
        return Api.ok(hint.<Map<String,Object>>map(value -> Map.of("available",true,"parcel",value))
            .orElseGet(() -> Map.of("available",false)));
    }
    @GetMapping("/complexes/kapt/{kaptCode}")
    public Map<String,Object> detail(@PathVariable String kaptCode, JwtAuthenticationToken token){
        limiter.check("kapt-global","all",120,60);
        limiter.check("kapt-member",token.getName(),20,60);
        return Api.ok(kapt.detail(kaptCode).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND")));
    }
    @PostMapping("/organizations/{id}/complexes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> register(@PathVariable long id,@Valid @RequestBody Register req,JwtAuthenticationToken token){
        limiter.check("complex-register",token.getName(),10,60);
        if(req.kaptCode()!=null&&!req.kaptCode().isBlank())limiter.check("kapt-global","all",120,60);
        return Api.ok(service.register(actor(token),id,req.kaptCode(),req.displayName(),req.roadAddress()));
    }
    @GetMapping("/platform/complex-managements/pending")
    public Map<String,Object> pending(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(service.pending(actor(token),page,size));
    }
    @GetMapping("/platform/complex-managements/{id}/address-candidates")
    public Map<String,Object> candidates(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(service.candidates(actor(token),id,page,size));
    }
    @PatchMapping("/platform/complex-managements/{id}/decision")
    public Map<String,Object> decide(@PathVariable long id,@Valid @RequestBody Decision req,JwtAuthenticationToken token){
        return Api.ok(service.decide(actor(token),id,req.approve(),req.reason(),Boolean.TRUE.equals(req.confirmDistinctAddress())));
    }
    @GetMapping("/organizations/{id}/complex-managements")
    public Map<String,Object> managements(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(service.managements(actor(token),id,page,size));
    }
    @GetMapping("/organizations/{id}/complexes/mine")
    public Map<String,Object> mine(@PathVariable long id,JwtAuthenticationToken token){
        return Api.ok(service.mine(actor(token),id));
    }
    @GetMapping("/organizations/{id}/complexes/{complexId}/members")
    public Map<String,Object> accessMembers(@PathVariable long id,@PathVariable long complexId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(service.accessMembers(actor(token),id,complexId,page,size));
    }
    @PatchMapping("/organizations/{id}/complexes/{complexId}/members/{memberId}/access")
    public Map<String,Object> access(@PathVariable long id,@PathVariable long complexId,@PathVariable long memberId,@Valid @RequestBody Access req,JwtAuthenticationToken token){
        return Api.ok(service.access(actor(token),id,complexId,memberId,req.grant()));
    }
}
