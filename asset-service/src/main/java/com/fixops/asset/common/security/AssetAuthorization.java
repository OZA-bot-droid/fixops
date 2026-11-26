package com.fixops.asset.common.security;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** Gateway가 인증 후 전달한 사용자 ID·역할·조직 범위로 Asset 도메인 접근 권한을 확인한다. */
@Component
public class AssetAuthorization {
    private final RestClient client;
    private final String key;
    public AssetAuthorization(@Value("${fixops.member.url:http://127.0.0.1:8081}") String url,
                                 @Value("${fixops.internal.asset-authz-key:}") String key) {
        this.key=key;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public record Scope(Long organizationId,Long complexId,Long locationId){}
    public record Decision(Long memberId,Long membershipId,Long organizationId,String role,Boolean allowed){}
    public record Envelope(Boolean success,Decision data){}
    public void requireRead(String bearer,Long org,Long complex){
        require(bearer,org,complex,Set.of("ADMIN","TECHNICIAN"));
    }
    public void requireWrite(String bearer,Long org,Long complex){
        require(bearer,org,complex,Set.of("ADMIN"));
    }
    private void require(String bearer,Long org,Long complex,Set<String> roles){
        if(bearer==null||!bearer.startsWith("Bearer ")||bearer.length()<=7)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");
        if(key.length()<43)throw unavailable();
        Envelope response;
        try{
            response=client.post().uri("/internal/authorization/check")
                .header("Authorization",bearer).header("X-FixOps-Service","asset")
                .header("X-FixOps-Service-Key",key).body(new Scope(org,complex,null))
                .retrieve().body(Envelope.class);
        }catch(RestClientResponseException ex){
            int status=ex.getStatusCode().value();
            if(status==401)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED");
            if(status==403)throw denied();
            throw unavailable();
        }catch(RestClientException ex){throw unavailable();}
        if(response==null||!Boolean.TRUE.equals(response.success())||response.data()==null)throw unavailable();
        var d=response.data();
        if(!Boolean.TRUE.equals(d.allowed())||d.memberId()==null||d.memberId()<=0||d.membershipId()==null||d.membershipId()<=0||!org.equals(d.organizationId())||d.role()==null||!roles.contains(d.role()))throw denied();
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
}
