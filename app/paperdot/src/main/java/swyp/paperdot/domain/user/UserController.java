package swyp.paperdot.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import swyp.paperdot.common.JwtAuthFilter.PaperdotPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    // GET /users/me
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal PaperdotPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        UserEntity user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}