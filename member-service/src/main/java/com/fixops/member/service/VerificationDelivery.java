package com.fixops.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.mail.MessagingException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class VerificationDelivery {
    private static final Logger log = LoggerFactory.getLogger(VerificationDelivery.class);
    private final Environment environment;
    private final String emailProvider, smsProvider, endpoint, secret, demoEmails, demoPhones, mailFrom;
    private final ObjectProvider<JavaMailSender> mailSender;
    @Value("${fixops.onboarding.public-url:}") private String onboardingPublicUrl;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public VerificationDelivery(Environment environment,
        @Value("${fixops.verification.email-provider:disabled}") String emailProvider,
        @Value("${fixops.verification.sms-provider:disabled}") String smsProvider,
        ObjectProvider<JavaMailSender> mailSender,
        @Value("${fixops.verification.mail-from:}") String mailFrom,
        @Value("${fixops.verification.endpoint:}") String endpoint,
        @Value("${fixops.verification.secret:}") String secret,
        @Value("${fixops.verification.demo-emails:}") String demoEmails,
        @Value("${fixops.verification.demo-phones:}") String demoPhones){
        this.environment=environment;this.emailProvider=emailProvider;this.smsProvider=smsProvider;this.mailSender=mailSender;this.mailFrom=mailFrom;this.endpoint=endpoint;this.secret=secret;this.demoEmails=demoEmails;this.demoPhones=demoPhones;
    }
    @PostConstruct
    void validate(){
        if(!Set.of("disabled","mock","webhook","smtp").contains(emailProvider)||!Set.of("disabled","mock","webhook").contains(smsProvider))
            throw new IllegalStateException("Unsupported verification provider");
        if("smtp".equals(emailProvider)&&(mailFrom.isBlank()||mailSender.getIfAvailable()==null
            ||environment.getProperty("spring.mail.username","").isBlank()
            ||environment.getProperty("spring.mail.password","").isBlank()))
            throw new IllegalStateException("SMTP verification requires MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM");
        if(("mock".equals(emailProvider)||"mock".equals(smsProvider))&&(!Arrays.asList(environment.getActiveProfiles()).contains("demo")||
            Arrays.stream(environment.getActiveProfiles()).anyMatch(p->Set.of("prod","production").contains(p))))
            throw new IllegalStateException("Mock verification requires demo-only profile");
    }
    private String provider(String purpose){
        return switch(purpose){case "EMAIL"->emailProvider;case "RESIDENT"->smsProvider;default->throw unavailable();};
    }
    public boolean residentSmsEnabled(){return !"disabled".equals(smsProvider);}
    public boolean mock(String purpose){return "mock".equals(provider(purpose));}
    public void send(String purpose,String destination,String code){
        String provider=provider(purpose);
        if(mock(purpose)){
            String allowed="EMAIL".equals(purpose)?demoEmails:demoPhones;
            if(Arrays.stream(allowed.split(",")).map(String::trim).noneMatch(destination::equals))
                throw unavailable();
            return;
        }
        if("smtp".equals(provider)){
            try {
                var sender = mailSender.getObject();
                var message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(mailFrom, "FixOps");
                helper.setTo(destination);
                helper.setSubject("[FixOps] 이메일 인증번호");
                String plain = "안녕하세요. FixOps입니다.\n\n"
                    + "안전한 서비스 이용을 위해 아래 인증번호를 인증 화면에 입력해 주세요.\n\n"
                    + "이메일 인증번호: " + code + "\n유효시간: 5분\n\n"
                    + "본인이 요청하지 않은 인증이라면 이 이메일을 무시해 주세요.\n\n"
                    + "감사합니다.\nFixOps 운영팀\n\n본 메일은 발신 전용입니다.";
                String html;
                try (var input = new ClassPathResource("templates/email/email-verification.html").getInputStream()) {
                    html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (!code.matches("[0-9]{6}")) throw unavailable();
                helper.setText(plain, html.replace("{{CODE}}", code));
                sender.send(message);
                return;
            } catch (MailException | MessagingException | IOException ex) {
                log.error("SMTP verification delivery failed: {}", ex.getClass().getSimpleName());
                throw unavailable();
            }
        }
        if(!"webhook".equals(provider)||!endpoint.startsWith("https://")||secret.isBlank())throw unavailable();
        try{
            String body="purpose="+encode(purpose)+"&destination="+encode(destination)+"&code="+encode(code);
            var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+secret).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200||response.statusCode()>=300)throw unavailable();
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable();}
        catch(java.io.IOException|IllegalArgumentException ex){throw unavailable();}
    }
    public boolean notifyOrganizationInvitation(String destination){
        if(!"smtp".equals(emailProvider)) return false;
        try {
            var sender=mailSender.getObject();
            var message=sender.createMimeMessage();
            var helper=new MimeMessageHelper(message,false,StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom,"FixOps");
            helper.setTo(destination);
            helper.setSubject("[FixOps] 조직 개설 초대");
            String body="FixOps 조직 개설 초대가 도착했습니다. 초대받은 이메일로 회원가입 또는 로그인하고 이메일 인증을 완료한 뒤, 조직 가입·초대 화면에서 조직 개설 초대를 수락해 주세요. 유효기간은 72시간입니다.";
            String link=onboardingLink();
            helper.setText(link.isEmpty()?body:body+"\n\n초대 확인: "+link);
            sender.send(message);
            return true;
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException ex) {
            log.warn("Organization invitation email delivery failed: {}",ex.getClass().getSimpleName());
            return false;
        }
    }
    private String onboardingLink(){
        try {
            URI uri=URI.create(onboardingPublicUrl.trim());
            if(!Set.of("https","http").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||"/".equals(uri.getPath())))return "";
            return uri.getScheme()+"://"+uri.getRawAuthority()+"/login?onboarding=invitation";
        }catch(IllegalArgumentException|NullPointerException ex){return "";}
    }
    private String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
}
