package com.communityott.user.service;

import com.communityott.common.exception.ProfileNotFoundException;
import com.communityott.common.exception.UserNotFoundException;
import com.communityott.user.dto.CreateProfileRequest;
import com.communityott.user.dto.ProfileResponse;
import com.communityott.user.dto.UpdateProfileRequest;
import com.communityott.user.entity.Profile;
import com.communityott.user.entity.User;
import com.communityott.user.repository.ProfileRepository;
import com.communityott.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProfileResponse createProfile(Long userId, CreateProfileRequest request) {
        log.info("Creating profile '{}' for user ID: {}", request.getDisplayName(), userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        long existingCount = profileRepository.countByUserId(userId);
        boolean shouldBeDefault = existingCount == 0 || Boolean.TRUE.equals(request.getIsDefault());

        if (shouldBeDefault && existingCount > 0) {
            clearDefaultProfile(userId);
        }

        Profile profile = Profile.builder()
                .user(user)
                .displayName(request.getDisplayName().trim())
                .avatarUrl(request.getAvatarUrl())
                .preferredLanguage(request.getPreferredLanguage() != null && !request.getPreferredLanguage().isBlank()
                        ? request.getPreferredLanguage().trim()
                        : "en")
                .isDefault(shouldBeDefault)
                .build();

        Profile savedProfile = profileRepository.save(profile);
        log.info("Successfully created profile ID: {} for user ID: {}", savedProfile.getId(), userId);

        return ProfileResponse.fromEntity(savedProfile);
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> getProfilesForUser(Long userId) {
        log.debug("Fetching all profiles for user ID: {}", userId);
        return profileRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(ProfileResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByIdAndUser(Long profileId, Long userId) {
        log.debug("Fetching profile ID: {} for user ID: {}", profileId, userId);
        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));

        return ProfileResponse.fromEntity(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(Long profileId, Long userId, UpdateProfileRequest request) {
        log.info("Updating profile ID: {} for user ID: {}", profileId, userId);

        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            profile.setDisplayName(request.getDisplayName().trim());
        }

        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl().isBlank() ? null : request.getAvatarUrl().trim());
        }

        if (request.getPreferredLanguage() != null && !request.getPreferredLanguage().isBlank()) {
            profile.setPreferredLanguage(request.getPreferredLanguage().trim());
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) && !profile.isDefault()) {
            clearDefaultProfile(userId);
            profile.setDefault(true);
        }

        Profile updatedProfile = profileRepository.save(profile);
        log.info("Successfully updated profile ID: {}", updatedProfile.getId());

        return ProfileResponse.fromEntity(updatedProfile);
    }

    @Transactional
    public void deleteProfile(Long profileId, Long userId) {
        log.info("Deleting profile ID: {} for user ID: {}", profileId, userId);

        Profile profile = profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId));

        boolean wasDefault = profile.isDefault();
        profileRepository.delete(profile);

        if (wasDefault) {
            List<Profile> remaining = profileRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!remaining.isEmpty()) {
                Profile newDefault = remaining.getFirst();
                newDefault.setDefault(true);
                profileRepository.save(newDefault);
                log.info("Set profile ID: {} as new default profile for user ID: {}", newDefault.getId(), userId);
            }
        }

        log.info("Successfully deleted profile ID: {}", profileId);
    }

    private void clearDefaultProfile(Long userId) {
        profileRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(p -> {
            p.setDefault(false);
            profileRepository.save(p);
        });
    }
}
