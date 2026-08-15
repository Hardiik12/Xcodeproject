package com.communityott.user.repository;

import com.communityott.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Profile> findByIdAndUserId(Long id, Long userId);

    Optional<Profile> findByUserIdAndIsDefaultTrue(Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndDisplayNameIgnoreCase(Long userId, String displayName);
}
