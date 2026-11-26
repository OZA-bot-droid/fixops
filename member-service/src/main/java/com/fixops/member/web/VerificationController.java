package com.fixops.member.web;

import com.fixops.member.service.VerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/members/email-verification")
public class VerificationController {
    private final VerificationService service;
    public VerificationController(VerificationService service){this.service=service;}
    public record Verify(@NotNull UUID challengeId,@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
    @PostMapping("/request")
    public ResponseEntity<Map<String,Object>> request(JwtAuthenticationToken token){
        var issued=service.email(Long.parseLong(token.getName()));
        var data=new LinkedHashMap<String,Object>();
        data.put("challengeId",issued.challengeId());
        if(issued.demoCode()!=null)data.put("demoCode",issued.demoCode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(data));
    }
    @PostMapping("/verify")
    public Map<String,Object> verify(@Valid @RequestBody Verify request,JwtAuthenticationToken token){
        if(!service.verifyEmail(Long.parseLong(token.getName()),request.challengeId(),request.code()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(Map.of("emailVerified",true));
    }
}
