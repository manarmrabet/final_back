package com.example.CWMS.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionRequest {
    private String name;
    private String warehouseCode;
    private String warehouseLabel;
}