package com.stock.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteDataDTO {
    private String time;
    private BigDecimal price;
    private Long volume;
    private BigDecimal avgPrice;
}
