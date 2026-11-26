package com.fixops.member.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/internal/authorization")
public class InternalAuthorizationController {
    private final JdbcTemplate db;
    private final Map<String,String> keys;
    public InternalAuthorizationController(JdbcTemplate db,
        @Value("${fixops.internal.asset-authz-key:}") String asset,
        @Value("${fixops.internal.workorder-authz-key:}") String workorder,
        @Value("${fixops.internal.inventory-authz-key:}") String inventory,
        @Value("${fixops.internal.ai-authz-key:}") String ai){
        this.db=db;this.keys=Map.of("asset",asset,"workorder",workorder,"inventory",inventory,"ai",ai);
    }
    public record Scope(@NotNull @Positive Long organizationId,@Positive Long complexId,@Positive Long locationId){}
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    @PostMapping("/check")
    public Map<String,Object> check(@RequestHeader(value="X-FixOps-Service",required=false) String service,
        @RequestHeader(value="X-FixOps-Service-Key",required=false) String credential,
        @Valid @RequestBody Scope scope,JwtAuthenticationToken token){
        String expected=service==null?"":keys.getOrDefault(service,"");
        if(expected.length()<43||credential==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),credential.getBytes(StandardCharsets.UTF_8)))
            throw denied();
        long member=Long.parseLong(token.getName());
        var rows=db.queryForList("""
            select om.id,om.role,o.kind from organization_members om
            join organizations o on o.id=om.organization_id
            join members m on m.id=om.member_id
            where om.member_id=? and om.organization_id=? and om.status='ACTIVE' and o.status='ACTIVE' and m.email_verified=true
            """,member,scope.organizationId());
        if(rows.size()!=1)throw denied();
        var row=rows.getFirst();long membership=((Number)row.get("id")).longValue();
        String role=(String)row.get("role");
        if(!Set.of("ADMIN","TECHNICIAN","USER").contains(role))throw denied();
        if("BUILDING".equals(row.get("kind"))){
            if(scope.complexId()==null)throw denied();
            boolean allowed=Boolean.TRUE.equals(db.queryForObject("""
                select exists(select 1 from member_complex_access a
                join complex_managements cm on cm.complex_id=a.complex_id and cm.status='ACTIVE'
                join apartment_complexes c on c.id=a.complex_id and c.verification_status='VERIFIED'
                where a.organization_member_id=? and a.complex_id=? and cm.organization_id=? and a.status='ACTIVE' and a.access_scope=?)
                """,Boolean.class,membership,scope.complexId(),scope.organizationId(),"USER".equals(role)?"RESIDENT":role));
            if(!allowed)throw denied();
            if("USER".equals(role)){
                if(scope.locationId()==null||!Boolean.TRUE.equals(db.queryForObject("""
                    select exists(select 1 from resident_units u join roster_entries e on e.id=u.roster_entry_id
                    join resident_rosters r on r.id=e.roster_id
                    where u.organization_member_id=? and u.complex_id=? and u.location_id=? and u.status='ACTIVE'
                    and e.status='ACTIVE' and r.status='ACTIVE' and r.organization_id=? and r.complex_id=u.complex_id)
                    """,Boolean.class,membership,scope.complexId(),scope.locationId(),scope.organizationId())))throw denied();
            }
        }else if(!"FACTORY".equals(row.get("kind"))||scope.complexId()!=null)throw denied();
        // This proves membership scope only; callers must check operation, assignment and resource ownership.
        return Api.ok(Map.of("memberId",member,"membershipId",membership,"organizationId",scope.organizationId(),"role",role,"allowed",true));
    }
}
