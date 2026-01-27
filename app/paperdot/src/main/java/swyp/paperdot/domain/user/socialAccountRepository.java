package swyp.paperdot.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface socialAccountRepository extends JpaRepository<socialAccountEntity, Long> {
    Optional<socialAccountEntity> findByProviderAndProviderUserId(socialProvider provider, String providerUserId);
}
