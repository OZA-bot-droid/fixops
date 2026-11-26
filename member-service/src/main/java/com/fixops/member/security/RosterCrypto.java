package com.fixops.member.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Component
public class RosterCrypto {
    private final String encryptionKey;
    private final String lookupKey;
    private final SecureRandom random = new SecureRandom();
    public RosterCrypto(@Value("${fixops.roster.encryption-key:}") String encryptionKey,
                        @Value("${fixops.roster.lookup-key:}") String lookupKey) {
        this.encryptionKey=encryptionKey; this.lookupKey=lookupKey;
    }
    private byte[] key(String value) {
        try {
            byte[] bytes=Base64.getDecoder().decode(value);
            if(bytes.length==32)return bytes;
        }catch(IllegalArgumentException ignored){}
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");
    }
    public void requireConfigured() {
        if(Arrays.equals(key(encryptionKey),key(lookupKey)))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");
    }
    public String normalize(String contact) {
        if(contact==null||!contact.matches("[0-9+ ()-]{9,30}"))throw new IllegalArgumentException();
        String digits=contact.replaceAll("[^0-9]","");
        if(digits.startsWith("82")&&contact.startsWith("+"))digits="0"+digits.substring(2);
        if(!digits.matches("01[016789][0-9]{7,8}"))throw new IllegalArgumentException();
        return digits;
    }
    public String lookup(long rosterId,String normalized) {
        requireConfigured();
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(lookupKey),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((rosterId+":"+normalized).getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Roster cryptography unavailable");}
    }
    public String encrypt(long rosterId,String normalized) {
        requireConfigured();
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(encryptionKey),"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD(("roster:"+rosterId).getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Roster cryptography unavailable");}
    }
}
