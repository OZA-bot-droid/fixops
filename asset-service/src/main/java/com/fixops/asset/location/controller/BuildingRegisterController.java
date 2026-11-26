package com.fixops.asset.location.controller;

import com.fixops.asset.common.security.AssetAuthorization;
import com.fixops.asset.location.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/locations/building-register")
public class BuildingRegisterController {
    private final AssetAuthorization auth;
    private final BuildingUnitImportService service;
    public BuildingRegisterController(AssetAuthorization auth,BuildingUnitImportService service){this.auth=auth;this.service=service;}
    public record PreviewRequest(@NotNull @Positive Long organizationId,@NotNull @Positive Long complexId,
                                 @NotNull BuildingRegisterClient.Parcel parcel){}
    public record CommitRequest(@NotNull @Positive Long organizationId,@NotNull @Positive Long complexId,
                                @NotNull @Positive Long rootId,@NotNull BuildingRegisterClient.Parcel parcel,
                                @NotEmpty @Size(max=10000) List<@NotBlank @Size(max=100) String> selectedIds,
                                @NotNull Boolean confirmedParcel){}
    @PostMapping("/preview")
    public BuildingRegisterClient.Preview preview(@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody PreviewRequest req){
        auth.requireWrite(bearer,req.organizationId(),req.complexId());
        return service.preview(req.parcel());
    }
    @PostMapping("/commit")
    public BuildingUnitImportService.Result commit(@RequestHeader(value="Authorization",required=false) String bearer,@Valid @RequestBody CommitRequest req){
        auth.requireWrite(bearer,req.organizationId(),req.complexId());
        if(!Boolean.TRUE.equals(req.confirmedParcel()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        return service.commit(req.organizationId(),req.complexId(),req.rootId(),req.parcel(),req.selectedIds());
    }
}
