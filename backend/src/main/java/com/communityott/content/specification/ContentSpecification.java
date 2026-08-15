package com.communityott.content.specification;

import com.communityott.content.dto.ContentFilterCriteria;
import com.communityott.content.entity.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ContentSpecification {

    private ContentSpecification() {}

    public static Specification<Content> withCriteria(ContentFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Publication / Lifecycle Status
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            // 2. Content Type
            if (criteria.getContentType() != null) {
                predicates.add(cb.equal(root.get("contentType"), criteria.getContentType()));
            }

            // 3. Age Rating
            if (criteria.getAgeRating() != null) {
                predicates.add(cb.equal(root.get("ageRating"), criteria.getAgeRating()));
            }

            // 4. Category Filter (by slug or ID)
            if (criteria.getCategory() != null && !criteria.getCategory().trim().isEmpty()) {
                String catFilter = criteria.getCategory().trim();
                Join<Content, ContentCategory> categoryJoin = root.join("contentCategories", JoinType.INNER);
                Join<ContentCategory, Category> cat = categoryJoin.join("category", JoinType.INNER);

                Predicate activeCat = cb.isTrue(cat.get("active"));

                if (isNumeric(catFilter)) {
                    Long catId = Long.parseLong(catFilter);
                    predicates.add(cb.and(cb.equal(cat.get("id"), catId), activeCat));
                } else {
                    predicates.add(cb.and(
                            cb.or(
                                    cb.equal(cb.lower(cat.get("slug")), catFilter.toLowerCase()),
                                    cb.equal(cb.lower(cat.get("name")), catFilter.toLowerCase())
                            ),
                            activeCat
                    ));
                }
            }

            // 5. Language Filter (matches either originalLanguage OR available contentLanguages)
            if (criteria.getLanguage() != null && !criteria.getLanguage().trim().isEmpty()) {
                String langFilter = criteria.getLanguage().trim();

                // Check original language
                Join<Content, Language> origLangJoin = root.join("originalLanguage", JoinType.LEFT);
                Predicate origLangMatch;
                if (isNumeric(langFilter)) {
                    Long langId = Long.parseLong(langFilter);
                    origLangMatch = cb.and(
                            cb.equal(origLangJoin.get("id"), langId),
                            cb.isTrue(origLangJoin.get("active"))
                    );
                } else {
                    origLangMatch = cb.and(
                            cb.or(
                                    cb.equal(cb.lower(origLangJoin.get("code")), langFilter.toLowerCase()),
                                    cb.equal(cb.lower(origLangJoin.get("name")), langFilter.toLowerCase())
                            ),
                            cb.isTrue(origLangJoin.get("active"))
                    );
                }

                // Check available content languages
                Join<Content, ContentLanguage> contentLangJoin = root.join("contentLanguages", JoinType.LEFT);
                Join<ContentLanguage, Language> availLang = contentLangJoin.join("language", JoinType.LEFT);
                Predicate availLangMatch;
                if (isNumeric(langFilter)) {
                    Long langId = Long.parseLong(langFilter);
                    availLangMatch = cb.and(
                            cb.equal(availLang.get("id"), langId),
                            cb.isTrue(availLang.get("active"))
                    );
                } else {
                    availLangMatch = cb.and(
                            cb.or(
                                    cb.equal(cb.lower(availLang.get("code")), langFilter.toLowerCase()),
                                    cb.equal(cb.lower(availLang.get("name")), langFilter.toLowerCase())
                            ),
                            cb.isTrue(availLang.get("active"))
                    );
                }

                predicates.add(cb.or(origLangMatch, availLangMatch));
            }

            // 6. Search Term (case-insensitive ILIKE match across title, subtitle, description, and tags)
            if (criteria.getSearch() != null && !criteria.getSearch().trim().isEmpty()) {
                String pattern = "%" + criteria.getSearch().trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate subtitleMatch = cb.like(cb.lower(root.get("subtitle")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                Predicate tagsMatch = cb.like(cb.lower(root.get("tags")), pattern);

                predicates.add(cb.or(titleMatch, subtitleMatch, descMatch, tagsMatch));
            }

            // Distinct to prevent duplicate rows when joining collections
            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
