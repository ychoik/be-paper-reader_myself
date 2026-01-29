package swyp.paperdot.domain.user;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${paperdot.jwt.refresh-cookie-name}")
    private String refreshCookieName;

    @PostMapping("/token")
    public Map<String, Object> token(
            @CookieValue(name = "${paperdot.jwt.refresh-cookie-name}", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("No refresh cookie");
        }

        // DB에 저장된 refresh인지 + 만료/폐기 체크
        RefreshTokenEntity valid = refreshTokenService.getValid(refreshToken);

        Long userId = valid.getUser().getId();
        String access = jwtService.createAccessToken(userId);

        return Map.of("accessToken", access);
    }

    @PostMapping("/logout")
    public void logout(
            @CookieValue(name = "${paperdot.jwt.refresh-cookie-name}", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }

        // 쿠키 삭제
        ResponseCookie deleteCookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(true) // 운영 https면 true 아니면 false
                .sameSite("None") //배포시
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", deleteCookie.toString());
    }
}
