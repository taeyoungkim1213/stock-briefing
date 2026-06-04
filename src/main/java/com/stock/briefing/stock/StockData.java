package com.stock.briefing.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockData {
    private String name;
    private String price;
    private String change;
    private String changeRate;
}
