package swyp.paperdot.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface refreshTokenRepository extends JpaRepository<refreshTokenEntity, Long> {
    Optional<refreshTokenEntity> findByTokenHash(String tokenHash);
    void deleteByUser_Id(Long userId);
}
