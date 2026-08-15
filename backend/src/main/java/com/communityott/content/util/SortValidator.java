package com.communityott.content.util;

import com.communityott.common.exception.InvalidSortFieldException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class SortValidator {

    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "releaseDate",
            "createdAt",
            "title",
            "durationSeconds",
            "updatedAt"
    );

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private SortValidator() {}

    public static Pageable validateAndSanitizePageable(Pageable pageable) {
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = pageable.getPageSize();

        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            for (Sort.Order order : sort) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new InvalidSortFieldException(order.getProperty(), ALLOWED_SORT_FIELDS);
                }
            }
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
