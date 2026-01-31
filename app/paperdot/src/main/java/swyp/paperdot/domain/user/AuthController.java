package swyp.paperdot.domain.user;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import swyp.paperdot.common.JwtAuthFilter;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthAppService authAppService;

    @Value("${paperdot.jwt.refresh-cookie-name}")
    private String refreshCookieName;

    // refresh 쿠키로 access 토큰 발급 (기존 그대로)
    @PostMapping("/token")
    public Map<String, Object> token(
            @CookieValue(name = "${paperdot.jwt.refresh-cookie-name}", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("No refresh cookie");
        }

        RefreshTokenEntity valid = refreshTokenService.getValid(refreshToken);

        Long userId = valid.getUser().getId();
        String access = jwtService.createAccessToken(userId);

        return Map.of("accessToken", access);
    }

    //로그아웃 (공통)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Long userId = resolveUserId(request);

        authAppService.logout(userId);

        ResponseCookie deleteCookie = deleteRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

    // 카카오 회원탈퇴
    @DeleteMapping("/withdraw/kakao")
    public ResponseEntity<Void> withdrawKakao(HttpServletRequest request) {
        Long userId = resolveUserId(request);

        authAppService.withdrawKakao(userId);

        ResponseCookie deleteCookie = deleteRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

    // 구글 회원탈퇴
    @DeleteMapping("/withdraw/google")
    public ResponseEntity<Void> withdrawGoogle(HttpServletRequest request) {
        Long userId = resolveUserId(request);

        authAppService.withdrawGoogle(userId);

        ResponseCookie deleteCookie = deleteRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

    // access(SecurityContext) -> refresh cookie 순으로 userId 획득
    private Long resolveUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthFilter.PaperdotPrincipal p) {
            return p.userId();
        }

        String refreshToken = readCookie(request, refreshCookieName);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("No access token and no refresh cookie");
        }

        RefreshTokenEntity valid = refreshTokenService.getValid(refreshToken);
        return valid.getUser().getId();
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(true) // 로컬 http면 false, 운영 https면 true
                .sameSite("None") //운영일때만
                .path("/")
                .maxAge(0)
                .build();
    }
}
