package com.casepackoptimizer.service;

import com.casepackoptimizer.dto.CasepackOptimizerRequest;
import com.casepackoptimizer.dto.CasepackOptimizerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CasepackOptimizerServiceTest {

    private CasepackOptimizerService service;

    @BeforeEach
    void setUp() {
        service = new CasepackOptimizerService();
    }

    @Test
    void testGenericFairShareScenario() {
        // Test with completely generic values
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 4, 10), // Any size ratios
                Map.of("str1", 100, "str2", 150, "str3", 200, "str4", 250), // Any number of stores
                Map.of("wh1", 15, "wh2", 10, "wh3", 12, "wh4", 6) // Any number of warehouses
        );

        CasepackOptimizerResponse response = service.optimizeCasepacks(request);

        assertNotNull(response);
        verifyResponse(response, request);
    }

    @Test
    void testDifferentSizeRatios() {
        // Test with different ratio patterns

        // Single ratio
        CasepackOptimizerRequest request1 = createRequest(
                Arrays.asList(5), // Only bundles of 5
                Map.of("storeA", 50, "storeB", 75, "storeC", 100),
                Map.of("warehouse1", 20, "warehouse2", 15)
        );
        CasepackOptimizerResponse response1 = service.optimizeCasepacks(request1);
        verifyResponse(response1, request1);

        // Complex ratio
        CasepackOptimizerRequest request2 = createRequest(
                Arrays.asList(2, 3, 4, 5), // Multiple ratios
                Map.of("shop1", 80, "shop2", 120, "shop3", 160, "shop4", 200, "shop5", 240),
                Map.of("depot1", 25, "depot2", 30, "depot3", 15)
        );
        CasepackOptimizerResponse response2 = service.optimizeCasepacks(request2);
        verifyResponse(response2, request2);
    }

    @Test
    void testSurplusScenario() {
        // Test when available > need
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 2, 3), // 6 items per casepack
                Map.of("store1", 10, "store2", 20, "store3", 30), // Total need: 60
                Map.of("wh1", 50, "wh2", 50) // 100 casepacks = 600 items (surplus)
        );

        CasepackOptimizerResponse response = service.optimizeCasepacks(request);

        // Should satisfy all needs
        int totalItemsAllocated = response.getStores().entrySet().stream()
                .mapToInt(e -> e.getValue() * 6)
                .sum();

        assertTrue(totalItemsAllocated >= 60);
        assertTrue(response.getRemainingSupply() > 0);
    }

    @Test
    void testZeroNeedStores() {
        // Test with some stores having zero need
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 4),
                Map.of("store1", 50, "store2", 0, "store3", 100, "store4", 0),
                Map.of("wh1", 20, "wh2", 15)
        );

        CasepackOptimizerResponse response = service.optimizeCasepacks(request);

        assertEquals(0, response.getStores().get("store2"));
        assertEquals(0, response.getStores().get("store4"));
    }

    @Test
    void testLargeScaleScenario() {
        // Test with many stores and warehouses
        Map<String, Integer> stores = new HashMap<>();
        Map<String, Integer> warehouses = new HashMap<>();

        // Create 50 stores with random needs
        Random rand = new Random(42); // Fixed seed for reproducibility
        for (int i = 1; i <= 50; i++) {
            stores.put("store" + i, rand.nextInt(500) + 50);
        }

        // Create 10 warehouses with random inventory
        for (int i = 1; i <= 10; i++) {
            warehouses.put("warehouse" + i, rand.nextInt(100) + 10);
        }

        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 3, 6, 12), // Variable ratios
                stores,
                warehouses
        );

        CasepackOptimizerResponse response = service.optimizeCasepacks(request);
        verifyResponse(response, request);
    }

    // Helper method to create request
    private CasepackOptimizerRequest createRequest(List<Integer> ratios,
                                                   Map<String, Integer> stores,
                                                   Map<String, Integer> warehouses) {
        CasepackOptimizerRequest request = new CasepackOptimizerRequest();

        // Create casepack with given ratios
        CasepackOptimizerRequest.CasePack casePack = new CasepackOptimizerRequest.CasePack();
        casePack.setPacks(1); // Not used in calculation

        List<CasepackOptimizerRequest.CasePack.SizeRatio> sizeRatios = new ArrayList<>();
        for (Integer ratio : ratios) {
            sizeRatios.add(new CasepackOptimizerRequest.CasePack.SizeRatio(ratio));
        }
        casePack.setSizeRatios(sizeRatios);
        request.setCasePacks(Collections.singletonList(casePack));

        request.setNeedPerStore(new LinkedHashMap<>(stores));
        request.setWarehouseAvailableQty(new LinkedHashMap<>(warehouses));

        return request;
    }

    // Helper method to verify response
    private void verifyResponse(CasepackOptimizerResponse response, CasepackOptimizerRequest request) {
        // Calculate items per casepack
        int itemsPerCasepack = request.getCasePacks().get(0).getSizeRatios().stream()
                .mapToInt(r -> r.getQty())
                .sum();

        // Verify store allocations don't exceed available
        int totalAllocatedCasepacks = response.getStores().values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        int totalAvailableCasepacks = request.getWarehouseAvailableQty().values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        assertTrue(totalAllocatedCasepacks <= totalAvailableCasepacks,
                "Allocated casepacks should not exceed available");

        // Verify warehouse distributions match allocated
        int totalWarehouseDistributed = response.getWarehouses().values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        assertEquals(totalAllocatedCasepacks, totalWarehouseDistributed,
                "Warehouse distributions should match total allocated");

        // Verify remaining supply
        assertEquals(totalAvailableCasepacks - totalAllocatedCasepacks,
                response.getRemainingSupply(),
                "Remaining supply calculation should be correct");

        // Print summary for debugging
        System.out.println("\n=== Test Summary ===");
        System.out.println("Items per casepack: " + itemsPerCasepack);
        System.out.println("Total available casepacks: " + totalAvailableCasepacks);
        System.out.println("Total allocated casepacks: " + totalAllocatedCasepacks);
        System.out.println("Remaining casepacks: " + response.getRemainingSupply());

        System.out.println("\nStore allocations:");
        response.getStores().forEach((store, packs) -> {
            int need = request.getNeedPerStore().get(store);
            int allocated = packs * itemsPerCasepack;
            System.out.printf("  %s: need=%d, allocated=%d items (%d packs)%n",
                    store, need, allocated, packs);
        });
    }
}