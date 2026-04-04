package com.example.CWMS.dto;

import lombok.*;
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
}