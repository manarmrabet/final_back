package com.example.CWMS.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectTemplateDTO {
    private Long id;
    private String name;
    private List<String> fields;
    private boolean active;

    // ✅ FIX SpotBugs — setter avec copie défensive
    public void setFields(List<String> fields) {
        this.fields = fields == null ? null : new ArrayList<>(fields);
    }

    // ✅ FIX SpotBugs — getter protégé
    public List<String> getFields() {
        return fields == null ? null : Collections.unmodifiableList(fields);
    }
}