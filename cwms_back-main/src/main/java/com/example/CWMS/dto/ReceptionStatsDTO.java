package com.example.CWMS.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
 public class ReceptionStatsDTO {
     private long totalOrders;
     private BigDecimal totalQuantityOrdered;
     private BigDecimal totalQuantityReceived;
     private BigDecimal totalValue;
     private double receiptRate;
     private long supplierCount;
 }