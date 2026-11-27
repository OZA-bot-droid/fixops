package com.fixops.member.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class RequestRateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> SCRIPT=new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 or redis.call('TTL',KEYS[1])<0 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class);
    public RequestRateLimiter(StringRedisTemplate redis){this.redis=redis;}
    public void check(String operation,String identity,int limit,int seconds){
        String digest;
        try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
        Long count;
        try{count=redis.execute(SCRIPT,List.of("fixops:rate:"+operation+":"+digest),Integer.toString(seconds));}
        catch(DataAccessException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"SESSION_STORE_UNAVAILABLE");}
        if(count==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"SESSION_STORE_UNAVAILABLE");
        if(count>limit)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED");
    }
}
