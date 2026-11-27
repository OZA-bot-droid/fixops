package com.fixops.member.web;

import com.fixops.member.service.OrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class OrganizationController {
    private final OrganizationService svc;

    public OrganizationController(OrganizationService svc) {
        this.svc = svc;
    }

    public record Apply(@NotBlank @Size(max = 200) String name, @NotBlank String kind) {
    }

    public record Decision(boolean approve, String reason) {
    }

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> apply(@Valid @RequestBody Apply req, JwtAuthenticationToken token) {
        return Api.ok(svc.apply(Long.valueOf(token.getName()), req.name(), req.kind()));
    }

    @GetMapping("/organizations/mine")
    public Map<String, Object> mine(JwtAuthenticationToken token) {
        return Api.ok(svc.mine(Long.valueOf(token.getName())));
    }

    @GetMapping("/platform/organizations/pending")
    public Map<String, Object> pending(JwtAuthenticationToken token) {
        return Api.ok(svc.pending(Long.valueOf(token.getName())));
    }

    @PatchMapping("/platform/organizations/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id, @RequestBody Decision req, JwtAuthenticationToken token) {
        return Api.ok(svc.decide(Long.valueOf(token.getName()), id, req.approve(), req.reason()));
    }
}
