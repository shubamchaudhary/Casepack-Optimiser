package com.casepackoptimizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CasepackOptimizerResponse {

    @JsonProperty("casePacks")
    private List<CasepackOptimizerRequest.CasePack> casePacks;

    @JsonProperty("stores")
    private Map<String, Integer> stores;

    @JsonProperty("warehouses")
    private Map<String, Integer> warehouses;

    @JsonProperty("remainingSupply")
    private int remainingSupply;
}