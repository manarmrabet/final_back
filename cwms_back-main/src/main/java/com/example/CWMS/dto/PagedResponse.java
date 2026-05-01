package com.example.CWMS.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.data.domain.Page;
import java.util.Collections;
import java.util.List;



public record PagedResponse<T>(

        @JsonProperty("content")
        List<T> content,

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
    // ✅ FIX SpotBugs — constructeur compact protège content
    public PagedResponse {
        content = content == null ? null
                : Collections.unmodifiableList(content);
    }

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