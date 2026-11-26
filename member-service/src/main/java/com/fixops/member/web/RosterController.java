package com.fixops.member.web;

import com.fixops.member.service.RosterService;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/complexes/{complexId}/roster-imports")
public class RosterController {
    private final RosterService service;
    public RosterController(RosterService service){this.service=service;}
    private long actor(JwtAuthenticationToken token){return Long.parseLong(token.getName());}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> upload(@PathVariable long complexId,
            @RequestParam String mode,@RequestPart("file") MultipartFile file,JwtAuthenticationToken token){
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(Api.ok(service.upload(actor(token),complexId,mode,file)));
    }
    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String,Object>> preview(@PathVariable long complexId,@PathVariable long jobId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken token){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Api.ok(service.preview(actor(token),complexId,jobId,page,size)));
    }
    @PostMapping("/{jobId}/commit")
    public Map<String,Object> commit(@PathVariable long complexId,@PathVariable long jobId,
            @RequestParam(defaultValue="false") boolean acknowledgeMissing,JwtAuthenticationToken token){
        return Api.ok(service.commit(actor(token),complexId,jobId,acknowledgeMissing));
    }
}
