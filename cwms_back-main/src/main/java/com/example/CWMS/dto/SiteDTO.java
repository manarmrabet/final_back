package com.example.CWMS.dto;

import java.util.Date;
import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteDTO {
    private int siteId;
    private String siteName;
    private Date createdAt;
    private Date updatedAt;


}