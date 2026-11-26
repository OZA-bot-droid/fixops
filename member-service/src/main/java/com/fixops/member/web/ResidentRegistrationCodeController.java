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
@RequestMapping("/complexes/{complexId}/resident-registration-codes")
public class ResidentRegistrationCodeController {
    private final ResidentEnrollmentService service;
    public ResidentRegistrationCodeController(ResidentEnrollmentService service){this.service=service;}
    private long actor(JwtAuthenticationToken token){return Long.parseLong(token.getName());}
    public record Issue(@NotNull @Positive Long rosterEntryId){}
    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@PathVariable long complexId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(complexId<=0||page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(service.registrationEntries(actor(token),complexId,page,size)));
    }
    @PostMapping
    public ResponseEntity<Map<String,Object>> issue(@PathVariable long complexId,@Valid @RequestBody Issue req,JwtAuthenticationToken token){
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(Api.ok(service.issueRegistrationCode(actor(token),complexId,req.rosterEntryId())));
    }
    @DeleteMapping("/{codeId}")
    public ResponseEntity<Void> revoke(@PathVariable long complexId,@PathVariable long codeId,JwtAuthenticationToken token){
        service.revokeRegistrationCode(actor(token),complexId,codeId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
