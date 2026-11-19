package com.fixops.member.service;
import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*;
@Service
public class AuthService {
 private final MemberRepository members; private final OrganizationMemberRepository memberships; private final RefreshSessionRepository sessions;
 private final PasswordEncoder encoder; private final JwtEncoder jwtEncoder; private final SecureRandom random=new SecureRandom();
 @Value("${fixops.jwt.issuer}") private String issuer;
 @Value("${fixops.jwt.audience}") private String audience;
 @Value("${fixops.jwt.access-minutes:15}") private int accessMinutes;
 @Value("${fixops.jwt.refresh-days:7}") private int refreshDays;
 @Value("${fixops.demo.email-verification-bypass:false}") private boolean demoVerify;
 public AuthService(MemberRepository m,OrganizationMemberRepository om,RefreshSessionRepository s,PasswordEncoder e,JwtEncoder j){members=m;memberships=om;sessions=s;encoder=e;jwtEncoder=j;}
 @Transactional public Member signup(String email,String password,String name){
  if(members.findByEmail(email.trim().toLowerCase(Locale.ROOT)).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS");
  return members.save(new Member(email.trim().toLowerCase(Locale.ROOT),encoder.encode(password),name.trim(),demoVerify));
 }
 @Transactional public Map<String,Object> login(String email,String password){
  Member member=members.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS"));
  if(!encoder.matches(password,member.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS");
  return issue(member);
 }
 @Transactional public Map<String,Object> refresh(String raw){
  RefreshSession s=sessions.findByTokenHash(sha(raw)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN"));
  if(s.revoked || !s.expiresAt.isAfter(Instant.now())) {
   // stolen/expired token cannot reissue access tokens
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN");
  }
  s.revoked=true; sessions.saveAndFlush(s);
  Member member=members.findById(s.memberId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  return issue(member);
 }
 @Transactional public void logout(String raw){sessions.findByTokenHash(sha(raw)).ifPresent(s->{s.revoked=true;sessions.save(s);});}
 private Map<String,Object> issue(Member m){
  Instant now=Instant.now();
  var active=memberships.findByMemberIdAndStatus(m.id,"ACTIVE");
  var orgs=active.stream().map(x->Map.of("organizationId",x.organizationId,"role",x.role)).toList();
  JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(accessMinutes)))
   .subject(m.id.toString()).claim("email",m.email).claim("platform_admin",m.platformAdmin).claim("memberships",orgs).build();
  String token=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("fixops-key-1").build(),claims)).getTokenValue();
  byte[] buf=new byte[32];random.nextBytes(buf);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  sessions.save(new RefreshSession(UUID.randomUUID(),m.id,sha(raw),now.plus(Duration.ofDays(refreshDays))));
  return Map.of("accessToken",token,"refreshToken",raw,"tokenType","Bearer","expiresIn",accessMinutes*60,"member",Map.of("id",m.id,"email",m.email,"name",m.displayName,"platformAdmin",m.platformAdmin,"memberships",orgs));
 }
 private String sha(String s){try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
