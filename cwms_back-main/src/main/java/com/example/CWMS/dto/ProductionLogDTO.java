package com.example.CWMS.dto;

import lombok.Data;

@Data
public class ProductionLogDTO {
    private Long    id;
    private String  lotCode;
    private String  itemCode;
    private String  warehouse;
    private String  location;
    private Double  qtyBefore;
    private Double  qtyRequested;
    private Double  qtyAfter;
    private Double  qtyDelta;
    private String  operationType;
    private String  status;
    private Long    userId;
    private String  userName;
    private String  deviceInfo;
    private String  source;
    private String  createdAt;
    private String  notes;
    private boolean stockVide;
    private String  errorMessage;
}