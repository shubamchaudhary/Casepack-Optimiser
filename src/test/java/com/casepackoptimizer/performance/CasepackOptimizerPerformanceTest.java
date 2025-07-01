package com.casepackoptimizer.performance;

import com.casepackoptimizer.dto.CasepackOptimizerRequest;
import com.casepackoptimizer.service.CasepackOptimizerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CasepackOptimizerPerformanceTest {

    private CasepackOptimizerService service;

    @BeforeEach
    void setUp() {
        service = new CasepackOptimizerService();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void testLargeScalePerformance() {
        // Test with 1000 stores and 100 warehouses
        Map<String, Integer> stores = new HashMap<>();
        Map<String, Integer> warehouses = new HashMap<>();

        Random random = new Random(42);

        // Create 1000 stores with random needs
        for (int i = 1; i <= 1000; i++) {
            stores.put("store" + i, random.nextInt(1000) + 100);
        }

        // Create 100 warehouses with random inventory
        for (int i = 1; i <= 100; i++) {
            warehouses.put("warehouse" + i, random.nextInt(500) + 50);
        }

        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 5, 10, 25),
                stores,
                warehouses
        );

        long startTime = System.currentTimeMillis();
        var response = service.optimizeCasepacks(request);
        long endTime = System.currentTimeMillis();

        System.out.printf("Large scale optimization took %d ms for 1000 stores and 100 warehouses%n",
                endTime - startTime);

        assertNotNull(response);
        assertEquals(1000, response.getStores().size());
        assertEquals(100, response.getWarehouses().size());

        // Should complete within 1 second
        assertTrue(endTime - startTime < 1000);
    }

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void testMediumScalePerformance() {
        // Test with 100 stores and 20 warehouses
        Map<String, Integer> stores = new HashMap<>();
        Map<String, Integer> warehouses = new HashMap<>();

        for (int i = 1; i <= 100; i++) {
            stores.put("store" + i, i * 100);
        }

        for (int i = 1; i <= 20; i++) {
            warehouses.put("warehouse" + i, i * 50);
        }

        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(2, 3, 5, 7, 11),
                stores,
                warehouses
        );

        long startTime = System.currentTimeMillis();
        var response = service.optimizeCasepacks(request);
        long endTime = System.currentTimeMillis();

        System.out.printf("Medium scale optimization took %d ms for 100 stores and 20 warehouses%n",
                endTime - startTime);

        assertNotNull(response);
        assertTrue(endTime - startTime < 500);
    }

    @Test
    void testStressWithComplexRatios() {
        // Test with many size ratios
        List<Integer> complexRatios = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20);

        Map<String, Integer> stores = new HashMap<>();
        Map<String, Integer> warehouses = new HashMap<>();

        for (int i = 1; i <= 50; i++) {
            stores.put("store" + i, i * 250);
        }

        for (int i = 1; i <= 10; i++) {
            warehouses.put("warehouse" + i, i * 100);
        }

        CasepackOptimizerRequest request = createRequest(
                complexRatios,
                stores,
                warehouses
        );

        long startTime = System.currentTimeMillis();
        var response = service.optimizeCasepacks(request);
        long endTime = System.currentTimeMillis();

        System.out.printf("Complex ratio optimization took %d ms with %d different ratios%n",
                endTime - startTime, complexRatios.size());

        assertNotNull(response);

        // Calculate items per pack
        int itemsPerPack = complexRatios.stream().mapToInt(Integer::intValue).sum();
        System.out.printf("Items per casepack: %d%n", itemsPerPack);
    }

    @Test
    void testMemoryEfficiency() {
        // Monitor memory usage for large scale operations
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Force garbage collection before test

        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        // Run multiple iterations
        for (int iteration = 0; iteration < 10; iteration++) {
            Map<String, Integer> stores = new HashMap<>();
            Map<String, Integer> warehouses = new HashMap<>();

            for (int i = 1; i <= 500; i++) {
                stores.put("store" + i, i * 100);
            }

            for (int i = 1; i <= 50; i++) {
                warehouses.put("warehouse" + i, i * 100);
            }

            CasepackOptimizerRequest request = createRequest(
                    Arrays.asList(1, 2, 3, 4, 5),
                    stores,
                    warehouses
            );

            service.optimizeCasepacks(request);
        }

        runtime.gc(); // Force garbage collection after test
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = (afterMemory - beforeMemory) / (1024 * 1024); // Convert to MB
        System.out.printf("Memory used for 10 iterations: %d MB%n", memoryUsed);

        // Should not use excessive memory
        assertTrue(memoryUsed < 100, "Should use less than 100MB for 10 iterations");
    }

    private CasepackOptimizerRequest createRequest(List<Integer> ratios,
                                                   Map<String, Integer> stores,
                                                   Map<String, Integer> warehouses) {
        CasepackOptimizerRequest request = new CasepackOptimizerRequest();

        CasepackOptimizerRequest.CasePack casePack = new CasepackOptimizerRequest.CasePack();
        casePack.setPacks(1);

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
}