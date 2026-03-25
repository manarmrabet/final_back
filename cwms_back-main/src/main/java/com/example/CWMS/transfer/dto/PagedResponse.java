package com.example.CWMS.transfer.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Wrapper de pagination stable pour la sérialisation JSON.
 *
 * Remplace Page<T> de Spring Data qui génère un warning
 * "Serializing PageImpl instances as-is is not supported".
 *
 * Structure JSON retournée :
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 42,
 *   "totalPages": 3,
 *   "first": true,
 *   "last": false
 * }
 */
public record PagedResponse<T>(
        List<T>  content,
        int      page,
        int      size,
        long     totalElements,
        int      totalPages,
        boolean  first,
        boolean  last
) {
    /** Constructeur depuis un Page<T> Spring Data */
    public static <T> PagedResponse<T> of(Page<T> p) {
        return new PagedResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isFirst(),
                p.isLast()
        );
    }
}