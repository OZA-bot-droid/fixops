package com.fixops.member.security;

import com.fixops.member.service.AuthSessionCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

// Bearer JWT의 서명/만료/issuer/audience 검증 이후, Member API 권한 검사 이전에 실행된다.
// Gateway 우회 호출에도 Redis SID 검증을 유지한다.
final class MemberSessionFilter extends OncePerRequestFilter {
    private final AuthSessionCache sessions;
    MemberSessionFilter(AuthSessionCache sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            boolean active;
            try {
                UUID sid = UUID.fromString(auth.getToken().getClaimAsString("sid"));
                Long memberId = Long.valueOf(auth.getToken().getSubject());
                active = sessions.isActive(sid, memberId);
            } catch (IllegalArgumentException | NullPointerException invalidClaim) {
                active = false;
            } catch (DataAccessException ex) {
                SecurityContextHolder.clearContext();
                AuthSecurityErrors.write(request, response, 503, "SESSION_STORE_UNAVAILABLE", "인증 세션 저장소에 연결할 수 없습니다.");
                return;
            }
            if (!active) {
                SecurityContextHolder.clearContext();
                AuthSecurityErrors.write(request, response, 401, "SESSION_NOT_ACTIVE", "활성 인증 세션이 없습니다.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
