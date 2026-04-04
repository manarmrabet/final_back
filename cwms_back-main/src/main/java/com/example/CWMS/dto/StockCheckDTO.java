package com.example.CWMS.dto;

import lombok.Data;

@Data
public class StockCheckDTO {
    private String  lotCode;
    private String  itemCode;
    private String  designation;
    private String  warehouse;
    private String  location;
    private Double  qtyAvailable;
    private String  unit;
    private boolean found;
    private boolean sufficient;
}