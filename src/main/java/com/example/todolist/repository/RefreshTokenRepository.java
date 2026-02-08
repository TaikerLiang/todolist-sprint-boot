package com.example.todolist.repository;

import com.example.todolist.model.RefreshToken;
import com.example.todolist.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByJti(String jti);

    List<RefreshToken> findByUserAndRevokedFalseAndExpiresAtAfter(User user, Instant now);

    List<RefreshToken> findByTokenFamilyId(String tokenFamilyId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.tokenFamilyId = :tokenFamilyId")
    void revokeTokenFamily(@Param("tokenFamilyId") String tokenFamilyId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    int revokeAllUserTokens(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold")
    void deleteExpiredTokens(@Param("threshold") Instant threshold);
}
