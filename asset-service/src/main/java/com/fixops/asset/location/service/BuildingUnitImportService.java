package com.fixops.asset.location.service;

import com.fixops.asset.location.entity.*;
import com.fixops.asset.location.repository.LocationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class BuildingUnitImportService {
    private final BuildingRegisterClient client;
    private final LocationRepository locations;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public BuildingUnitImportService(BuildingRegisterClient client,LocationRepository locations,JdbcTemplate db,TransactionTemplate tx){
        this.client=client;this.locations=locations;this.db=db;this.tx=tx;
    }
    public BuildingRegisterClient.Preview preview(BuildingRegisterClient.Parcel parcel){return client.preview(parcel);}
    public record Result(int createdBuildings,int createdUnits,int existingUnits){}
    public Result commit(long org,long complex,long rootId,BuildingRegisterClient.Parcel parcel,List<String> selectedIds){
        if(selectedIds==null||selectedIds.isEmpty()||selectedIds.size()>10000||new HashSet<>(selectedIds).size()!=selectedIds.size())
            throw bad();
        var selected=new HashSet<>(selectedIds);
        var units=client.preview(parcel).units().stream().filter(u->selected.contains(u.recordId())).toList();
        if(units.size()!=selected.size())throw conflict();
        // Re-read official records rather than trusting browser-supplied location codes.
        return tx.execute(status->{
            db.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 67182))","building-import:"+org+":"+complex);
            var all=locations.findAllByOrganizationIdAndComplexIdOrderById(org,complex);
            var root=all.stream().filter(l->l.getId().equals(rootId)&&l.getParentId()==null&&l.getLocationType()==LocationType.COMPLEX&&l.getStatus()==LocationStatus.ACTIVE)
                .findFirst().orElseThrow(BuildingUnitImportService::conflict);
            Map<Long,Map<String,Location>> children=new HashMap<>();
            for(var l:all)if(l.getParentId()!=null)children.computeIfAbsent(l.getParentId(),k->new HashMap<>()).put(l.getCode(),l);
            int buildings=0,created=0,existing=0;
            for(var u:units){
                var byCode=children.computeIfAbsent(rootId,k->new HashMap<>());
                var building=byCode.get(u.buildingNo());
                if(building==null){
                    building=locations.saveAndFlush(new Location(org,complex,root,LocationType.BUILDING,u.buildingNo(),u.buildingName()));
                    byCode.put(u.buildingNo(),building);buildings++;
                }
                if(building.getLocationType()!=LocationType.BUILDING||building.getStatus()!=LocationStatus.ACTIVE)throw conflict();
                var byUnit=children.computeIfAbsent(building.getId(),k->new HashMap<>());
                var old=byUnit.get(u.unitNo());
                if(old!=null){
                    if(old.getLocationType()!=LocationType.UNIT||old.getStatus()!=LocationStatus.ACTIVE)throw conflict();
                    existing++;continue;
                }
                var added=locations.save(new Location(org,complex,building,LocationType.UNIT,u.unitNo(),u.unitName()));
                byUnit.put(u.unitNo(),added);created++;
            }
            locations.flush();
            return new Result(buildings,created,existing);
        });
    }
    private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");}
}
