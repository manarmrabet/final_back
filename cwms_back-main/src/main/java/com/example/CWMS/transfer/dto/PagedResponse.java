package com.example.CWMS.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

/**
 * DTO de pagination — wrapper autour de Page<T> Spring.
 *
 * FIX : Le Page<T> Spring ne se sérialise pas bien par défaut en JSON
 * (il expose beaucoup de champs internes inutiles et peut causer des erreurs
 * de désérialisation côté Angular).
 *
 * Ce record produit exactement :
 * {
 *   "content":       [...],
 *   "page":          0,
 *   "size":          20,
 *   "totalElements": 42,
 *   "totalPages":    3,
 *   "first":         true,
 *   "last":          false
 * }
 *
 * Côté Angular, PagedResponse<T> est typé avec exactement ces champs.
 */
public record PagedResponse<T>(

        @JsonProperty("content")
        java.util.List<T> content,

        @JsonProperty("page")
        int page,

        @JsonProperty("size")
        int size,

        @JsonProperty("totalElements")
        long totalElements,

        @JsonProperty("totalPages")
        int totalPages,

        @JsonProperty("first")
        boolean first,

        @JsonProperty("last")
        boolean last

) {
    /**
     * Factory method — convertit un Page<T> Spring en PagedResponse<T>.
     * Utilisé dans le controller : PagedResponse.of(page)
     */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}