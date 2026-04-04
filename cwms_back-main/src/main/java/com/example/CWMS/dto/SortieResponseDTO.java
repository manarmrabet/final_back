package com.example.CWMS.dto;

import lombok.Data;

@Data
public class SortieResponseDTO {
    private boolean success;
    private String  message;
    private String  lotCode;
    private String  itemCode;
    private Double  qtyBefore;
    private Double  qtySortie;
    private Double  qtyAfter;
    private String  operationType;
    private Long    logId;
}