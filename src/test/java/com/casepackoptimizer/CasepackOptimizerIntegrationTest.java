package com.casepackoptimizer;

import com.casepackoptimizer.dto.CasepackOptimizerRequest;
import com.casepackoptimizer.dto.CasepackOptimizerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CasepackOptimizerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testOriginalFairShareScenario() throws Exception {
        // Your original example
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 4, 10),
                Map.of("str1", 100, "str2", 150, "str3", 200, "str4", 250),
                Map.of("wh1", 15, "wh2", 10, "wh3", 12, "wh4", 6)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Verify fair share allocation
        int itemsPerPack = 15;
        int totalAvailablePacks = 43;
        int totalAllocatedPacks = response.getStores().values().stream()
                .mapToInt(Integer::intValue).sum();

        assertTrue(totalAllocatedPacks <= totalAvailablePacks);
        assertEquals(0, response.getRemainingSupply());

        // Verify warehouse distribution
        int totalWarehouseDistribution = response.getWarehouses().values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(totalAllocatedPacks, totalWarehouseDistribution);
    }

    @Test
    void testSurplusScenario() throws Exception {
        // When available > need
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(5), // 5 items per pack
                Map.of("store1", 25, "store2", 50, "store3", 75), // Total need: 150
                Map.of("warehouse1", 100, "warehouse2", 50) // 150 packs = 750 items (surplus)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Should satisfy all needs exactly
        assertEquals(5, response.getStores().get("store1")); // 25/5 = 5 packs
        assertEquals(10, response.getStores().get("store2")); // 50/5 = 10 packs
        assertEquals(15, response.getStores().get("store3")); // 75/5 = 15 packs

        // Should have remaining supply
        assertEquals(120, response.getRemainingSupply()); // 150 - 30 = 120
    }

    @Test
    void testZeroNeedStores() throws Exception {
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 2, 3), // 6 items per pack
                Map.of("active1", 60, "closed1", 0, "active2", 120, "closed2", 0),
                Map.of("wh1", 40, "wh2", 35)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Verify zero need stores get zero allocation
        assertEquals(0, response.getStores().get("closed1"));
        assertEquals(0, response.getStores().get("closed2"));

        // Verify active stores get allocations
        assertTrue(response.getStores().get("active1") > 0);
        assertTrue(response.getStores().get("active2") > 0);
    }

    @ParameterizedTest
    @MethodSource("provideSizeRatioScenarios")
    void testVariousSizeRatios(List<Integer> sizeRatios, String testName) throws Exception {
        CasepackOptimizerRequest request = createRequest(
                sizeRatios,
                Map.of("store1", 100, "store2", 200, "store3", 300),
                Map.of("warehouse1", 50, "warehouse2", 40)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Verify basic constraints
        int totalAllocated = response.getStores().values().stream()
                .mapToInt(Integer::intValue).sum();
        assertTrue(totalAllocated <= 90, "Should not exceed available packs for " + testName);

        // Verify fair distribution
        int warehouseTotal = response.getWarehouses().values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(totalAllocated, warehouseTotal, "Warehouse distribution should match allocation");
    }

    @Test
    void testExactMatchScenario() throws Exception {
        // When items can be allocated exactly
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(10), // 10 items per pack
                Map.of("store1", 100, "store2", 200, "store3", 300), // Total: 600
                Map.of("warehouse1", 30, "warehouse2", 30) // 60 packs = 600 items (exact)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Should allocate exactly
        assertEquals(10, response.getStores().get("store1")); // 100/10 = 10
        assertEquals(20, response.getStores().get("store2")); // 200/10 = 20
        assertEquals(30, response.getStores().get("store3")); // 300/10 = 30
        assertEquals(0, response.getRemainingSupply());
    }

    @Test
    void testManyStoresAndWarehouses() throws Exception {
        // Large scale test
        Map<String, Integer> stores = new HashMap<>();
        Map<String, Integer> warehouses = new HashMap<>();

        // Create 20 stores
        for (int i = 1; i <= 20; i++) {
            stores.put("store" + i, i * 50);
        }

        // Create 5 warehouses
        for (int i = 1; i <= 5; i++) {
            warehouses.put("warehouse" + i, i * 100);
        }

        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 3, 5, 7),
                stores,
                warehouses
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Verify all stores are present in response
        assertEquals(20, response.getStores().size());
        assertEquals(5, response.getWarehouses().size());
    }

    @Test
    void testEdgeCaseEmptyStores() throws Exception {
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 2, 3),
                new HashMap<>(), // Empty stores
                Map.of("warehouse1", 50)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        assertTrue(response.getStores().isEmpty());
        assertEquals(50, response.getRemainingSupply());
    }

    @Test
    void testMinimalAllocation() throws Exception {
        // When available is much less than need
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(100), // 100 items per pack
                Map.of("store1", 1000, "store2", 2000, "store3", 3000), // Total: 6000
                Map.of("warehouse1", 5, "warehouse2", 5) // Only 10 packs = 1000 items
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // Should distribute proportionally
        int totalAllocated = response.getStores().values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(10, totalAllocated);

        // Store3 should get the most (highest need)
        assertTrue(response.getStores().get("store3") >= response.getStores().get("store2"));
        assertTrue(response.getStores().get("store2") >= response.getStores().get("store1"));
    }

    @Test
    void testSingleWarehouse() throws Exception {
        CasepackOptimizerRequest request = createRequest(
                Arrays.asList(1, 4, 10),
                Map.of("store1", 100, "store2", 150),
                Map.of("onlyWarehouse", 30)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/casepack/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CasepackOptimizerResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CasepackOptimizerResponse.class
        );

        // All allocation should come from the single warehouse
        int totalAllocated = response.getStores().values().stream()
                .mapToInt(Integer::intValue).sum();
        assertEquals(totalAllocated, response.getWarehouses().get("onlyWarehouse"));
    }

    // Helper method to create request
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

    // Test data provider for parameterized tests
    private static Stream<Arguments> provideSizeRatioScenarios() {
        return Stream.of(
                Arguments.of(Arrays.asList(1), "Single ratio"),
                Arguments.of(Arrays.asList(5), "Bundle of 5"),
                Arguments.of(Arrays.asList(1, 2), "Simple dual ratio"),
                Arguments.of(Arrays.asList(1, 4, 12), "Original ratio"),
                Arguments.of(Arrays.asList(2, 3, 4, 5), "Complex ratio"),
                Arguments.of(Arrays.asList(1, 10, 100), "Large differences"),
                Arguments.of(Arrays.asList(7, 14, 21, 28), "Multiples of 7")
        );
    }
}