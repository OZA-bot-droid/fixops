package com.fixops.member.service;

import com.fixops.member.security.RosterCrypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.util.*;

@Service
public class RosterService {
    private final JdbcTemplate db;
    private final RosterWorkbook workbook;
    private final RosterCrypto crypto;
    private final AuthService auth;
    public RosterService(JdbcTemplate db,RosterWorkbook workbook,RosterCrypto crypto,AuthService auth){
        this.db=db;this.workbook=workbook;this.crypto=crypto;this.auth=auth;
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"ROSTER_IMPORT_CONFLICT");}
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"ROSTER_VALIDATION_FAILED");}
    private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private long scope(long actor,long complex){
        var owners=db.queryForList("select organization_id from complex_managements where complex_id=? and status='ACTIVE'",complex);
        if(owners.isEmpty())throw denied();
        long org=number(owners.getFirst(),"organization_id");
        db.queryForList("select id from organizations where id=? for update",org);
        db.queryForList("select id from apartment_complexes where id=? for update",complex);
        boolean allowed=Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organizations o join complex_managements m on m.organization_id=o.id and m.status='ACTIVE' join apartment_complexes c on c.id=m.complex_id and c.verification_status='VERIFIED' join organization_members om on om.organization_id=o.id join member_complex_access a on a.organization_member_id=om.id and a.complex_id=c.id where o.id=? and o.status='ACTIVE' and c.id=? and om.member_id=? and om.role='ADMIN' and om.status='ACTIVE' and a.access_scope='ADMIN' and a.status='ACTIVE')",Boolean.class,org,complex,actor));
        if(!allowed)throw denied();
        return org;
    }
    private String sha(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Transactional
    public Map<String,Object> upload(long actor,long complex,String mode,MultipartFile file){
        long org=scope(actor,complex);
        if(!Set.of("PARTIAL","FULL").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        crypto.requireConfigured();
        var parsed=workbook.parse(file);
        db.update("insert into resident_rosters(complex_id,organization_id) values (?,?) on conflict(complex_id,organization_id) do nothing",complex,org);
        var roster=db.queryForList("select id,version from resident_rosters where complex_id=? and organization_id=? and status='ACTIVE' for update",complex,org);
        if(roster.isEmpty())throw conflict();
        long rid=number(roster.getFirst(),"id"),version=number(roster.getFirst(),"version");
        String digest=sha(parsed.bytes());
        var existing=db.queryForList("select id from roster_import_jobs where roster_id=? and file_sha256=? and base_version=? and mode=?",rid,digest,version,mode);
        if(!existing.isEmpty())return previewInternal(org,complex,number(existing.getFirst(),"id"),0,100);
        Long job=db.queryForObject("insert into roster_import_jobs(roster_id,organization_id,created_by,mode,status,file_sha256,base_version,row_count) values (?,?,?,?,'STAGING',?,?,?) returning id",Long.class,rid,org,actor,mode,digest,version,parsed.rows().size());
        Set<List<String>> keys=new HashSet<>();
        int errors=0;
        for(var row:parsed.rows()){
            String error=row.error(),lookup=null,cipher=null;
            if(error==null){
                try{
                    String normalized=crypto.normalize(row.contact());
                    lookup=crypto.lookup(rid,normalized);
                    if(!keys.add(List.of(row.building(),row.unit(),lookup)))error="DUPLICATE_ROW";
                    else cipher=crypto.encrypt(rid,normalized);
                }catch(IllegalArgumentException ex){error="INVALID_CONTACT";}
            }
            if(error!=null){
                errors++;
                db.update("insert into roster_staging_rows(import_job_id,row_no,validation_status,error_code) values (?,?,'INVALID',?)",job,row.rowNo(),error);
            }else{
                db.update("insert into roster_staging_rows(import_job_id,row_no,building_no,unit_no,resident_name,contact_ciphertext,contact_lookup_hmac,occupancy_status,move_in,move_out,validation_status) values (?,?,?,?,?,?,?,?,?,?,'VALID')",job,row.rowNo(),row.building(),row.unit(),row.name(),cipher,lookup,row.status(),row.moveIn(),row.moveOut());
            }
        }
        db.update("update roster_import_jobs set status=? where id=?",errors==0?"PREVIEW":"INVALID",job);
        return previewInternal(org,complex,job,0,100);
    }
    @Transactional
    public Map<String,Object> preview(long actor,long complex,long job,int page,int size){
        long org=scope(actor,complex);
        return previewInternal(org,complex,job,page,size);
    }
    private Map<String,Object> previewInternal(long org,long complex,long job,int page,int size){
        var rows=db.queryForList("select j.id,j.mode,j.status,j.row_count,j.base_version,j.created_at,j.committed_at from roster_import_jobs j join resident_rosters r on r.id=j.roster_id where j.id=? and j.organization_id=? and r.organization_id=? and r.complex_id=?",job,org,org,complex);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
        var result=new LinkedHashMap<String,Object>(rows.getFirst());
        result.put("rows",db.queryForList("select row_no,building_no,unit_no,occupancy_status,validation_status,error_code from roster_staging_rows where import_job_id=? order by row_no limit ? offset ?",job,size,(long)page*size));
        if("PREVIEW".equals(result.get("status"))){
            result.put("changes",db.queryForList("""
                select change_type,count(*) as count from (
                    select case when s.occupancy_status='MOVED_OUT' then 'EXPLICIT_MOVE_OUT'
                        when e.id is null then 'NEW'
                        when (e.name,e.status,e.move_in,e.move_out) is distinct from
                             (s.resident_name,s.occupancy_status,s.move_in,s.move_out) then 'CHANGED'
                        else 'UNCHANGED' end as change_type
                    from roster_staging_rows s join roster_import_jobs j on j.id=s.import_job_id
                    left join roster_entries e on e.roster_id=j.roster_id and e.building_no=s.building_no
                        and e.unit_no=s.unit_no and e.contact_lookup_hmac=s.contact_lookup_hmac
                    where s.import_job_id=? and s.validation_status='VALID'
                ) changes group by change_type order by change_type
                """,job));
            if("FULL".equals(result.get("mode"))){
                result.put("missingCount",db.queryForObject("""
                    select count(*) from roster_entries e join roster_import_jobs j on j.roster_id=e.roster_id
                    where j.id=? and e.status='ACTIVE' and not exists(
                        select 1 from roster_staging_rows s where s.import_job_id=j.id and s.validation_status='VALID'
                        and s.building_no=e.building_no and s.unit_no=e.unit_no and s.contact_lookup_hmac=e.contact_lookup_hmac)
                    """,Long.class,job));
                result.put("missingRows",db.queryForList("""
                    select e.id,e.building_no,e.unit_no from roster_entries e join roster_import_jobs j on j.roster_id=e.roster_id
                    where j.id=? and e.status='ACTIVE' and not exists(
                        select 1 from roster_staging_rows s where s.import_job_id=j.id and s.validation_status='VALID'
                        and s.building_no=e.building_no and s.unit_no=e.unit_no and s.contact_lookup_hmac=e.contact_lookup_hmac)
                    order by e.id limit ? offset ?
                    """,job,size,(long)page*size));
            }
        }
        return result;
    }
    @Transactional
    public Map<String,Object> commit(long actor,long complex,long job,boolean acknowledgeMissing){
        long org=scope(actor,complex);
        var jobs=db.queryForList("select j.id,j.roster_id,j.base_version,j.status,j.mode from roster_import_jobs j join resident_rosters r on r.id=j.roster_id where j.id=? and j.organization_id=? and r.organization_id=? and r.complex_id=? for update of j",job,org,org,complex);
        if(jobs.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");
        var j=jobs.getFirst();
        if("COMPLETE".equals(j.get("status")))return Map.of("id",job,"status","COMPLETE");
        if(!"PREVIEW".equals(j.get("status")))throw invalid();
        long rid=number(j,"roster_id");
        Long version=db.queryForObject("select version from resident_rosters where id=? and status='ACTIVE' for update",Long.class,rid);
        if(version!=number(j,"base_version"))throw conflict();
        if("FULL".equals(j.get("mode")) && !acknowledgeMissing &&
            Boolean.TRUE.equals(db.queryForObject("""
                select exists(select 1 from roster_entries e where e.roster_id=? and e.status='ACTIVE'
                  and not exists(select 1 from roster_staging_rows s where s.import_job_id=? and s.validation_status='VALID'
                  and s.building_no=e.building_no and s.unit_no=e.unit_no and s.contact_lookup_hmac=e.contact_lookup_hmac))
                """,Boolean.class,rid,job)))throw conflict();
        // Missing rows never imply move-out, including FULL imports.
        // Explicit MOVED_OUT rows alone revoke existing resident relationships.
        var affected=db.queryForList("select distinct om.member_id from roster_staging_rows s join roster_entries e on e.roster_id=? and e.building_no=s.building_no and e.unit_no=s.unit_no and e.contact_lookup_hmac=s.contact_lookup_hmac join resident_units u on u.roster_entry_id=e.id and u.status='ACTIVE' join organization_members om on om.id=u.organization_member_id where s.import_job_id=? and s.occupancy_status='MOVED_OUT' order by om.member_id",rid,job);
        db.update("insert into roster_entries(roster_id,building_no,unit_no,name,contact_ciphertext,contact_lookup_hmac,status,move_in,move_out) select ?,building_no,unit_no,resident_name,contact_ciphertext,contact_lookup_hmac,occupancy_status,move_in,move_out from roster_staging_rows where import_job_id=? and validation_status='VALID' on conflict(roster_id,building_no,unit_no,contact_lookup_hmac) do update set name=excluded.name,contact_ciphertext=excluded.contact_ciphertext,status=excluded.status,move_in=excluded.move_in,move_out=excluded.move_out",rid,job);
        // Invalidate pending applications before a later re-entry can reactivate the same roster row.
        db.update("""
            with rejected as (
                update resident_enrollments n set status='REJECTED',reviewed_by=?,reviewed_at=now()
                from roster_entries e
                where e.roster_id=? and e.status='MOVED_OUT' and n.roster_id=e.roster_id
                  and n.building_no=e.building_no and n.unit_no=e.unit_no and n.contact_lookup_hmac=e.contact_lookup_hmac
                  and n.status in ('PENDING_VERIFICATION','PENDING_REVIEW')
                returning n.id,n.challenge_id
            ), invalidated as (
                update verification_challenges v set consumed_at=now()
                from rejected r where v.id=r.challenge_id and v.consumed_at is null returning v.id
            )
            insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,after_state)
            select ?,'RESIDENT_MOVE_OUT_REJECT','resident_enrollment',id::text,?,'REJECTED' from rejected
            """,actor,rid,actor,org);
        // Revoke unused credentials for explicitly moved-out residents in the same transaction.
        db.update("update resident_registration_codes c set revoked_at=now() from roster_entries e where e.id=c.roster_entry_id and e.roster_id=? and e.status='MOVED_OUT' and c.consumed_at is null and c.revoked_at is null",rid);
        db.update("update resident_units u set status='REVOKED',revoked_at=now() from roster_entries e where e.id=u.roster_entry_id and e.roster_id=? and e.status='MOVED_OUT' and u.status='ACTIVE'",rid);
        for(var row:affected){
            long member=number(row,"member_id");
            db.update("update member_complex_access a set status='REVOKED' from organization_members om where om.id=a.organization_member_id and om.organization_id=? and om.member_id=? and a.complex_id=? and a.access_scope='RESIDENT' and not exists(select 1 from resident_units u where u.organization_member_id=om.id and u.complex_id=a.complex_id and u.status='ACTIVE')",org,member,complex);
            auth.revokeMemberSessions(member);
        }
        db.update("update resident_rosters set version=version+1 where id=?",rid);
        db.update("update roster_import_jobs set status='COMPLETE',committed_at=now() where id=?",job);
        db.update("delete from roster_staging_rows where import_job_id=?",job);
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,before_state,after_state) values (?,'ROSTER_COMMIT','roster_import',?,?,'PREVIEW','COMPLETE')",actor,Long.toString(job),org);
        return Map.of("id",job,"status","COMPLETE","version",version+1);
    }
}
