package com.casepackoptimizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CasepackOptimizerRequest {

    @JsonProperty("casePacks")
    private List<CasePack> casePacks;

    @JsonProperty("needPerStore")
    private Map<String, Integer> needPerStore;

    @JsonProperty("warehouseAvailableQty")
    private Map<String, Integer> warehouseAvailableQty;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CasePack {
        private int packs;
        private List<SizeRatio> sizeRatios;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SizeRatio {
            private int qty;
        }
    }
}