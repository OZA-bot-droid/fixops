package com.fixops.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class AssetUnitClient {
    private final RestClient client;
    private final String key;
    public AssetUnitClient(@Value("${fixops.asset.url:http://127.0.0.1:8082}") String url,
        @Value("${fixops.internal.member-unit-key:}") String key){
        this.key=key;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public record Unit(Long locationId,Long organizationId,Long complexId,String locationType){}
    public Optional<Long> resolve(long org,long complex,String building,String unit){
        if(key.length()<43)throw unavailable();
        try{
            Unit result=client.post().uri("/internal/member/units/resolve")
                .headers(h->h.setBearerAuth(key))
                .body(Map.of("organizationId",org,"complexId",complex,"buildingNo",building,"unitNo",unit))
                .retrieve().body(Unit.class);
            if(result==null||result.locationId()==null||result.locationId()<=0||
                !Long.valueOf(org).equals(result.organizationId())||!Long.valueOf(complex).equals(result.complexId())||!"UNIT".equals(result.locationType()))throw unavailable();
            return Optional.of(result.locationId());
        }catch(RestClientResponseException ex){
            if(ex.getStatusCode().value()==404||ex.getStatusCode().value()==409)return Optional.empty();
            throw unavailable();
        }catch(org.springframework.web.client.RestClientException ex){throw unavailable();}
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
}
