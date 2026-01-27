package swyp.paperdot.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import swyp.paperdot.domain.user.jwtService;

import java.io.IOException;
import java.util.List;

@Component
public class jwtAuthFilter extends OncePerRequestFilter {

    private final jwtService jwtService;

    public jwtAuthFilter(jwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                if ("access".equals(jwtService.getType(token))) {
                    Long userId = jwtService.getUserId(token);

                    var principal = new PaperdotPrincipal(userId);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 토큰 이상하면 그냥 인증 없이 진행 -> 보호된 API면 401/403 나감
            }
        }

        filterChain.doFilter(request, response);
    }

    public record PaperdotPrincipal(Long userId) {}
}
