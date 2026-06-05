package com.stock.briefing.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {
    private String name;
    private String symbol;
    private String price;
    private String change;
    private String changeRate;
    private String sector;
    private String market;
}
