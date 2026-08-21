package com.communityott.account.service;

import com.communityott.account.dto.LoginHistoryItemResponse;
import com.communityott.account.dto.LoginHistoryResponse;
import com.communityott.account.entity.UserLoginHistory;
import com.communityott.account.repository.UserLoginHistoryRepository;
import com.communityott.auth.entity.AuthSession;
import com.communityott.auth.entity.Platform;
import com.communityott.auth.repository.AuthSessionRepository;
import com.communityott.common.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final UserLoginHistoryRepository userLoginHistoryRepository;
    private final AuthSessionRepository authSessionRepository;

    @Transactional(readOnly = true)
    public LoginHistoryResponse getLoginHistory(
            Long userId,
            Integer page,
            Integer size,
            Instant fromDate,
            Instant toDate,
            String eventType,
            Platform platform,
            Long currentSessionId
    ) {
        int validatedPage = (page != null) ? page : 0;
        int validatedSize = (size != null) ? size : DEFAULT_PAGE_SIZE;

        if (validatedPage < 0) {
            throw new ApiException("Page index must not be negative", HttpStatus.BAD_REQUEST, "INVALID_PAGE");
        }
        if (validatedSize < 1) {
            throw new ApiException("Page size must be at least 1", HttpStatus.BAD_REQUEST, "INVALID_SIZE");
        }
        if (validatedSize > MAX_PAGE_SIZE) {
            validatedSize = MAX_PAGE_SIZE;
        }

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException("Invalid date range: 'from' must be before or equal to 'to'", HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }

        // Resolve current active device ID via active session
        Long currentDeviceId = null;
        if (currentSessionId != null) {
            currentDeviceId = authSessionRepository.findById(currentSessionId)
                    .filter(AuthSession::isActive)
                    .map(s -> s.getDeviceEntity() != null ? s.getDeviceEntity().getId() : null)
                    .orElse(null);
        }

        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Specification<UserLoginHistory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim().toUpperCase()));
            }
            if (platform != null) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toDate));
            }

            query.orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("id")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UserLoginHistory> historyPage = userLoginHistoryRepository.findAll(spec, pageable);

        Long finalCurrentDeviceId = currentDeviceId;
        List<LoginHistoryItemResponse> items = historyPage.getContent().stream()
                .map(item -> mapToItemResponse(item, finalCurrentDeviceId))
                .toList();

        return LoginHistoryResponse.builder()
                .items(items)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalItems(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .hasNext(historyPage.hasNext())
                .build();
    }

    private LoginHistoryItemResponse mapToItemResponse(UserLoginHistory item, Long currentDeviceId) {
        boolean isCurrent = false;
        if (currentDeviceId != null && item.getDevice() != null) {
            isCurrent = currentDeviceId.equals(item.getDevice().getId()) && item.getDevice().isActive();
        }

        return LoginHistoryItemResponse.builder()
                .id(item.getId())
                .event(item.getEventType())
                .status(item.getStatus())
                .deviceName(item.getDeviceName())
                .platform(item.getPlatform())
                .osVersion(item.getOsVersion())
                .appVersion(item.getAppVersion())
                .maskedIp(item.getMaskedIp())
                .approxLocation(item.getApproxLocation())
                .isCurrentDevice(isCurrent)
                .userMessage(item.getUserMessage())
                .occurredAt(item.getOccurredAt())
                .build();
    }
}
