package com.fixops.member.web;

import com.fixops.member.service.ResidentEnrollmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/resident-enrollments")
public class ResidentEnrollmentController {
    private final ResidentEnrollmentService service;
    public ResidentEnrollmentController(ResidentEnrollmentService service){this.service=service;}
    private long actor(JwtAuthenticationToken token){return Long.parseLong(token.getName());}
    private void page(int page,int size){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
    }
    public record Apply(@NotNull @Positive Long complexId,@NotBlank @Size(max=40) String buildingNo,
        @NotBlank @Size(max=40) String unitNo,@NotBlank @Size(max=30) String contact){}
    public record Verify(@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
    public record Decision(@NotNull Boolean approve){}
    public record Redeem(@NotNull @Positive Long complexId,@NotBlank @Pattern(regexp="[0-9a-fA-F]{32}") String code){}
    @PostMapping("/redeem-code")
    public ResponseEntity<Map<String,Object>> redeem(@Valid @RequestBody Redeem req,JwtAuthenticationToken token){
        var result=service.redeemRegistrationCode(actor(token),req.complexId(),req.code());
        if(!Boolean.TRUE.equals(result.get("verified")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(result));
    }
    @GetMapping("/verification-methods")
    public ResponseEntity<Map<String,Object>> methods(){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(service.verificationMethods()));
    }
    @GetMapping("/complexes")
    public ResponseEntity<Map<String,Object>> complexes(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        page(page,size);
        if(q.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(service.enrollable(actor(token),q.trim(),page,size)));
    }
    @PostMapping
    public ResponseEntity<Map<String,Object>> apply(@Valid @RequestBody Apply req,JwtAuthenticationToken token){
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(Api.ok(service.start(actor(token),req.complexId(),req.buildingNo(),req.unitNo(),req.contact())));
    }
    @PostMapping("/{id}/verify")
    public Map<String,Object> verify(@PathVariable long id,@Valid @RequestBody Verify req,JwtAuthenticationToken token){
        var result=service.verify(actor(token),id,req.code());
        if(!Boolean.TRUE.equals(result.get("verified")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return Api.ok(result);
    }
    @GetMapping("/mine")
    public Map<String,Object> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        page(page,size);return Api.ok(service.mine(actor(token),page,size));
    }
    @GetMapping("/pending")
    public Map<String,Object> pending(@RequestParam @Positive long complexId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        page(page,size);return Api.ok(service.pending(actor(token),complexId,page,size));
    }
    @PatchMapping("/{id}/decision")
    public Map<String,Object> decide(@PathVariable long id,@Valid @RequestBody Decision req,JwtAuthenticationToken token){
        return Api.ok(service.decide(actor(token),id,req.approve()));
    }
}
