package com.fixops.member.web;

import com.fixops.member.service.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
public class MembershipController {
    private final MembershipService service;
    public MembershipController(MembershipService service) { this.service=service; }
    private long actor(JwtAuthenticationToken token) { return Long.parseLong(token.getName()); }
    private void page(int page,int size) {
        if(page<0||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
    }
    public record Decision(@NotNull Boolean approve) {}
    public record Invite(@NotBlank @Email @Size(max=255) String email,@NotBlank String role) {}
    public record Accept(@NotBlank @Size(max=128) String token) {}
    public record Role(@NotBlank String role) {}

    @GetMapping("/organizations")
    public Map<String,Object> search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        page(page,size);
        if(q.length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(service.search(q,page,size));
    }
    @PostMapping("/organizations/{id}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> request(@PathVariable long id,JwtAuthenticationToken token) {
        return Api.ok(service.request(id,actor(token)));
    }
    @GetMapping("/organizations/{id}/join-requests")
    public Map<String,Object> pending(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token) {
        page(page,size); return Api.ok(service.pending(id,actor(token),page,size));
    }
    @GetMapping("/organizations/{id}/join-requests/mine")
    public Map<String,Object> mine(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token) {
        page(page,size); return Api.ok(service.mine(id,actor(token),page,size));
    }
    @PatchMapping("/organizations/{id}/join-requests/{requestId}/decision")
    public Map<String,Object> decide(@PathVariable long id,@PathVariable long requestId,@Valid @RequestBody Decision req,JwtAuthenticationToken token) {
        return Api.ok(service.decide(id,actor(token),requestId,req.approve()));
    }
    @PostMapping("/organizations/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Map<String,Object>> invite(@PathVariable long id,@Valid @RequestBody Invite req,JwtAuthenticationToken token) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(Api.ok(service.invite(id,actor(token),req.email(),req.role())));
    }
    @PostMapping("/invitations/accept")
    public Map<String,Object> accept(@Valid @RequestBody Accept req,JwtAuthenticationToken token) {
        return Api.ok(service.accept(actor(token),req.token()));
    }
    @GetMapping("/organizations/{id}/members")
    public Map<String,Object> members(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token) {
        page(page,size); return Api.ok(service.listMembers(id,actor(token),page,size));
    }
    @DeleteMapping("/organizations/{id}/members/{memberId}")
    public Map<String,Object> deactivate(@PathVariable long id,@PathVariable long memberId,JwtAuthenticationToken token){
        return Api.ok(service.deactivate(id,actor(token),memberId));
    }
    @DeleteMapping("/organizations/{id}/members/me")
    public Map<String,Object> leave(@PathVariable long id,JwtAuthenticationToken token){
        long member=actor(token);
        return Api.ok(service.deactivate(id,member,member));
    }
    @PatchMapping("/organizations/{id}/members/{memberId}/role")
    public Map<String,Object> role(@PathVariable long id,@PathVariable long memberId,@Valid @RequestBody Role req,JwtAuthenticationToken token) {
        return Api.ok(service.changeRole(id,actor(token),memberId,req.role()));
    }
}
