package com.fixops.member.web;
import com.fixops.member.service.AuthService;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/members")
public class AuthController {
 private final AuthService auth;
 public AuthController(AuthService auth){this.auth=auth;}
 public record Signup(@Email @NotBlank String email,@Size(min=12,max=100) String password,@NotBlank @Size(max=100) String name){}
 public record Login(@Email @NotBlank String email,@NotBlank String password){}
 public record Refresh(@NotBlank String refreshToken){}
 @PostMapping("/signup") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> signup(@Valid @RequestBody Signup req){
  var m=auth.signup(req.email(),req.password(),req.name());return Api.ok(Map.of("id",m.id,"email",m.email,"emailVerified",m.emailVerified));
 }
 @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody Login req){return Api.ok(auth.login(req.email(),req.password()));}
 @PostMapping("/refresh") public Map<String,Object> refresh(@Valid @RequestBody Refresh req){return Api.ok(auth.refresh(req.refreshToken()));}
 @PostMapping("/logout") public Map<String,Object> logout(@Valid @RequestBody Refresh req,JwtAuthenticationToken user){auth.logout(req.refreshToken());return Api.ok(Map.of("loggedOut",true));}
 @GetMapping("/me") public Map<String,Object> me(JwtAuthenticationToken user){return Api.ok(Map.of("id",Long.parseLong(user.getName()),"email",user.getToken().getClaimAsString("email"),"platformAdmin",Boolean.TRUE.equals(user.getToken().getClaim("platform_admin")),"memberships",user.getToken().getClaim("memberships")));}
}
