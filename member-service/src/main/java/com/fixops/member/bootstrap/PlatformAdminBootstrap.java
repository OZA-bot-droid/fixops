package com.fixops.member.bootstrap;

import com.fixops.member.model.Member;
import com.fixops.member.repo.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    @Value("${fixops.bootstrap.platform-email:}")
    private String email;
    @Value("${fixops.bootstrap.platform-password:}")
    private String password;

    public PlatformAdminBootstrap(MemberRepository members, PasswordEncoder encoder) {
        this.members = members;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 16)
            throw new IllegalStateException("Bootstrap admin requires email and password of 16+ characters");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (members.findByEmail(normalized).isPresent()) return;
        Member m = new Member(normalized, encoder.encode(password), "Platform Administrator", true);
        m.platformAdmin = true;
        members.save(m);
    }
}
