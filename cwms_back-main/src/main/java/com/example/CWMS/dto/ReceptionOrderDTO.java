package com.example.CWMS.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ReceptionOrderDTO {
    private String orderNumber;
    private String date;
    private String supplier;
    private String supplierCode;
    private BigDecimal totalQty;
    private String devise;
    private List<ReceptionLineDTO> lines;
}