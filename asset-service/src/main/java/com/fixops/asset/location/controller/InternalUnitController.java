package com.fixops.asset.location.controller;

import com.fixops.asset.location.repository.LocationRepository;
import com.fixops.asset.location.entity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/internal/member/units")
public class InternalUnitController {
    private final LocationRepository locations;
    private final String secret;
    public InternalUnitController(LocationRepository locations,@Value("${fixops.internal.member-unit-key:}") String secret){
        this.locations=locations;this.secret=secret;
    }
    public record Resolve(long organizationId,long complexId,String buildingNo,String unitNo){}
    @PostMapping("/resolve")
    @Transactional(readOnly=true)
    public Map<String,Object> resolve(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody Resolve req){
        if(secret.length()<43||authorization==null||!MessageDigest.isEqual(("Bearer "+secret).getBytes(StandardCharsets.UTF_8),authorization.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");
        if(req.organizationId()<=0||req.complexId()<=0||req.buildingNo()==null||req.unitNo()==null||req.buildingNo().isBlank()||req.unitNo().isBlank()||req.buildingNo().length()>40||req.unitNo().length()>40)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        var matches=locations.findAllByOrganizationIdAndComplexIdOrderById(req.organizationId(),req.complexId()).stream()
            .filter(u->u.getLocationType()==LocationType.UNIT&&u.getStatus()==LocationStatus.ACTIVE&&req.unitNo().equals(u.getCode()))
            .filter(u->{var b=u.getParent();if(b==null||b.getLocationType()!=LocationType.BUILDING||b.getStatus()!=LocationStatus.ACTIVE||!req.buildingNo().equals(b.getCode())||!b.getOrganizationId().equals(req.organizationId())||!Long.valueOf(req.complexId()).equals(b.getComplexId()))return false;
                var root=b.getParent();return root!=null&&root.getLocationType()==LocationType.COMPLEX&&root.getStatus()==LocationStatus.ACTIVE&&root.getOrganizationId().equals(req.organizationId())&&Long.valueOf(req.complexId()).equals(root.getComplexId());})
            .limit(2).toList();
        if(matches.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
        if(matches.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");
        return Map.of("locationId",matches.getFirst().getId(),"organizationId",req.organizationId(),"complexId",req.complexId(),"locationType","UNIT");
    }
}
