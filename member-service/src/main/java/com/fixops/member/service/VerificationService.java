package com.fixops.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class VerificationService {
    private final JdbcTemplate db;
    private final VerificationDelivery delivery;
    private final String secret;
    private final SecureRandom random=new SecureRandom();
    public VerificationService(JdbcTemplate db,VerificationDelivery delivery,
       @Value("${fixops.verification.hash-key:}") String secret){this.db=db;this.delivery=delivery;this.secret=secret;}
    private String hmac(String input){
        try{
            byte[] key=Base64.getDecoder().decode(secret);
            if(key.length!=32)throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        }catch(GeneralSecurityException|IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
    }
    private ResponseStatusException limited(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED");}
    public boolean residentSmsEnabled(){return delivery.residentSmsEnabled();}
    public record Issued(UUID challengeId,String demoCode){}
    @Transactional
    public Issued issue(long member,String purpose,String destination){
        db.queryForList("select id from members where id=? for update",member);
        String dest=hmac("destination:"+purpose+":"+destination);
        // Serialize challenges for the same destination across different accounts.
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",dest);
        if(db.queryForObject("select count(*) from verification_challenges where purpose=? and (member_id=? or destination_key=?) and created_at>now()-interval '1 minute'",Long.class,purpose,member,dest)>0)throw limited();
        if(db.queryForObject("select count(*) from verification_challenges where purpose=? and (member_id=? or destination_key=?) and created_at>now()-interval '1 hour'",Long.class,purpose,member,dest)>=5)throw limited();
        UUID id=UUID.randomUUID();
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        db.update("update verification_challenges set consumed_at=now() where member_id=? and purpose=? and consumed_at is null",member,purpose);
        db.update("insert into verification_challenges(id,member_id,purpose,destination_key,code_hash,expires_at) values (?,?,?,?,?,now()+interval '5 minutes')",id,member,purpose,dest,hmac("code:"+id+":"+code));
        delivery.send(purpose,destination,code);
        return new Issued(id,delivery.mock(purpose)?code:null);
    }
    // Caller must return normally on wrong code so the attempt counter commits.
    @Transactional
    public boolean consume(long member,String purpose,UUID id,String code,String destination){
        var rows=db.queryForList("select code_hash,destination_key,attempts from verification_challenges where id=? and member_id=? and purpose=? and consumed_at is null and expires_at>now() for update",id,member,purpose);
        if(rows.isEmpty())return false;
        var row=rows.getFirst();
        int attempts=((Number)row.get("attempts")).intValue();
        if(attempts>=5)return false;
        if(destination!=null&&!row.get("destination_key").equals(hmac("destination:"+purpose+":"+destination)))return false;
        boolean correct=MessageDigest.isEqual(((String)row.get("code_hash")).getBytes(StandardCharsets.US_ASCII),hmac("code:"+id+":"+code).getBytes(StandardCharsets.US_ASCII));
        if(correct)db.update("update verification_challenges set consumed_at=now() where id=?",id);
        else db.update("update verification_challenges set attempts=attempts+1 where id=?",id);
        return correct;
    }
    @Transactional
    public Issued email(long member){
        String email=db.queryForObject("select email from members where id=?",String.class,member);
        return issue(member,"EMAIL",email);
    }
    @Transactional
    public boolean verifyEmail(long member,UUID id,String code){
        String email=db.queryForObject("select email from members where id=? for update",String.class,member);
        if(!consume(member,"EMAIL",id,code,email))return false;
        db.update("update members set email_verified=true where id=?",member);
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id) values (?,'EMAIL_VERIFIED','member',?)",member,Long.toString(member));
        return true;
    }
}
