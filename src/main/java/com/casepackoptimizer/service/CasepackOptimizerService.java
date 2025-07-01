package com.casepackoptimizer.service;

import com.casepackoptimizer.dto.CasepackOptimizerRequest;
import com.casepackoptimizer.dto.CasepackOptimizerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CasepackOptimizerService {

    public CasepackOptimizerResponse optimizeCasepacks(CasepackOptimizerRequest request) {
        // Validate input
        if (request.getCasePacks() == null || request.getCasePacks().isEmpty()) {
            throw new IllegalArgumentException("Casepacks cannot be null or empty");
        }

        // Calculate total items per casepack (generic for any ratio)
        int itemsPerCasepack = calculateItemsPerCasepack(request.getCasePacks().get(0));

        // Calculate total available casepacks and items from all warehouses
        int totalAvailableCasepacks = request.getWarehouseAvailableQty().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int totalAvailableItems = totalAvailableCasepacks * itemsPerCasepack;

        // Calculate total need from all stores
        int totalNeed = request.getNeedPerStore().values().stream()
                .filter(need -> need > 0) // Only consider stores with positive needs
                .mapToInt(Integer::intValue)
                .sum();

        log.info("Items per casepack: {}, Total available casepacks: {}, Total available items: {}, Total need: {}",
                itemsPerCasepack, totalAvailableCasepacks, totalAvailableItems, totalNeed);

        // Calculate store allocations
        Map<String, Integer> storeAllocations = calculateStoreAllocations(
                request.getNeedPerStore(),
                totalAvailableItems,
                totalNeed,
                itemsPerCasepack
        );

        // Calculate total allocated casepacks
        int totalAllocatedCasepacks = storeAllocations.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // Calculate warehouse distributions based on fair share
        Map<String, Integer> warehouseDistributions = calculateWarehouseDistributions(
                request.getWarehouseAvailableQty(),
                totalAllocatedCasepacks
        );

        // Calculate remaining supply
        int remainingSupply = totalAvailableCasepacks - totalAllocatedCasepacks;

        return CasepackOptimizerResponse.builder()
                .casePacks(request.getCasePacks())
                .stores(storeAllocations)
                .warehouses(warehouseDistributions)
                .remainingSupply(remainingSupply)
                .build();
    }

    private int calculateItemsPerCasepack(CasepackOptimizerRequest.CasePack casePack) {
        return casePack.getSizeRatios().stream()
                .mapToInt(ratio -> ratio.getQty())
                .sum();
    }

    private Map<String, Integer> calculateStoreAllocations(Map<String, Integer> needPerStore,
                                                           int totalAvailableItems,
                                                           int totalNeed,
                                                           int itemsPerCasepack) {
        Map<String, Integer> allocations = new LinkedHashMap<>();
        Map<String, Double> expectedAllocations = new HashMap<>();

        // Handle edge case where total need is 0
        if (totalNeed == 0) {
            for (String store : needPerStore.keySet()) {
                allocations.put(store, 0);
            }
            return allocations;
        }

        if (totalAvailableItems < totalNeed) {
            // Fair share scenario - allocate proportionally
            for (Map.Entry<String, Integer> entry : needPerStore.entrySet()) {
                if (entry.getValue() > 0) {
                    double fairShareItems = (double) entry.getValue() * totalAvailableItems / totalNeed;
                    expectedAllocations.put(entry.getKey(), fairShareItems);
                } else {
                    expectedAllocations.put(entry.getKey(), 0.0);
                }
            }
        } else {
            // Sufficient supply scenario - satisfy all needs
            for (Map.Entry<String, Integer> entry : needPerStore.entrySet()) {
                expectedAllocations.put(entry.getKey(), (double) entry.getValue());
            }
        }

        // Convert expected items to casepacks using a greedy approach
        Map<String, Double> remainingNeeds = new HashMap<>(expectedAllocations);
        int remainingCasepacks = totalAvailableItems / itemsPerCasepack;

        // Initialize all stores with 0 casepacks
        for (String store : needPerStore.keySet()) {
            allocations.put(store, 0);
        }

        // Allocate casepacks to minimize total deviation
        while (remainingCasepacks > 0) {
            String bestStore = null;
            double maxPriority = Double.NEGATIVE_INFINITY;

            for (Map.Entry<String, Double> entry : remainingNeeds.entrySet()) {
                String store = entry.getKey();
                double remaining = entry.getValue();

                if (remaining > 0) {
                    // Priority based on how much this store needs relative to a casepack
                    double priority = remaining / itemsPerCasepack;

                    if (priority > maxPriority) {
                        maxPriority = priority;
                        bestStore = store;
                    }
                }
            }

            if (bestStore == null) {
                break;
            }

            // Allocate one casepack to the best store
            allocations.put(bestStore, allocations.get(bestStore) + 1);
            remainingNeeds.put(bestStore, remainingNeeds.get(bestStore) - itemsPerCasepack);
            remainingCasepacks--;
        }

        return allocations;
    }

    private Map<String, Integer> calculateWarehouseDistributions(Map<String, Integer> warehouseAvailableQty,
                                                                 int totalAllocatedCasepacks) {
        Map<String, Integer> distributions = new LinkedHashMap<>();
        int totalAvailable = warehouseAvailableQty.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        if (totalAllocatedCasepacks >= totalAvailable) {
            // Use all warehouse inventory
            return new LinkedHashMap<>(warehouseAvailableQty);
        }

        // Fair share distribution across warehouses
        int remaining = totalAllocatedCasepacks;
        List<Map.Entry<String, Integer>> sortedWarehouses = warehouseAvailableQty.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<String, Integer> entry : sortedWarehouses) {
            String warehouse = entry.getKey();
            int available = entry.getValue();

            // Calculate fair share for this warehouse
            double fairShare = (double) available * totalAllocatedCasepacks / totalAvailable;
            int allocation = Math.min((int) Math.round(fairShare), available);
            allocation = Math.min(allocation, remaining);

            distributions.put(warehouse, allocation);
            remaining -= allocation;
        }

        // Distribute any remaining casepacks
        while (remaining > 0) {
            for (Map.Entry<String, Integer> entry : sortedWarehouses) {
                String warehouse = entry.getKey();
                int available = entry.getValue();
                int allocated = distributions.get(warehouse);

                if (allocated < available && remaining > 0) {
                    distributions.put(warehouse, allocated + 1);
                    remaining--;
                }
            }
        }

        return distributions;
    }
}