package com.stock.briefing.stock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stock")
public class StockProperties {

    private Domestic domestic = new Domestic();
    private Us us = new Us();

    @Getter
    @Setter
    public static class Domestic {
        private List<ItemConfig> indices = new ArrayList<>();
        private Map<String, List<ItemConfig>> sectors = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Us {
        private Symbols symbols = new Symbols();
    }

    @Getter
    @Setter
    public static class Symbols {
        private List<ItemConfig> indices = new ArrayList<>();
        private List<ItemConfig> semiconductor = new ArrayList<>();
        private List<ItemConfig> bio = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ItemConfig {
        private String code;
        private String symbol;
        private String name;
    }
}
