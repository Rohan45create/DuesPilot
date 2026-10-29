package com.duespilot.api.web;

import com.duespilot.api.config.DuesPilotProperties;
import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.repository.LlmCallRepository;
import com.duespilot.api.repository.LlmCallRepository.ModelUsageRecord;
import com.duespilot.api.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("UsageController Web Tests (GET /api/v1/usage/models - Cost Meter, Section 12, 18)")
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmCallRepository llmCallRepository;

    @Test
    @DisplayName("GET /api/v1/usage/models returns 200 with model breakdowns and cost caps")
    void testGetModelUsage() throws Exception {
        List<ModelUsageRecord> mockRecords = List.of(
            new ModelUsageRecord("nvidia/nemotron-nano-3-30b-a3b", 42, 125000, 45000, 0, 43000L, 240),
            new ModelUsageRecord("nvidia/nemotron-3-super-120b-a12b", 12, 50000, 18000, 0, 83200L, 850),
            new ModelUsageRecord("nvidia/nemotron-3-ultra-550b-a55b", 2, 15000, 4500, 0, 55500L, 1600)
        );

        when(llmCallRepository.findUsageSummaryByModel()).thenReturn(mockRecords);
        when(llmCallRepository.findTotalCallsAllTime()).thenReturn(56L);
        when(llmCallRepository.findTotalCostAllTime()).thenReturn(181700L);
        when(llmCallRepository.findTotalCostToday()).thenReturn(45000L);

        mockMvc.perform(get("/api/v1/usage/models")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_calls").value(56))
            .andExpect(jsonPath("$.total_cost_micros").value(181700))
            .andExpect(jsonPath("$.daily_cost_micros").value(45000))
            .andExpect(jsonPath("$.daily_cost_cap_micros").isNumber())
            .andExpect(jsonPath("$.per_case_cost_cap_micros").isNumber())
            .andExpect(jsonPath("$.demo_mode").value("replay"))
            .andExpect(jsonPath("$.models").isArray())
            .andExpect(jsonPath("$.models[0].model").value("nvidia/nemotron-nano-3-30b-a3b"))
            .andExpect(jsonPath("$.models[0].calls").value(42))
            .andExpect(jsonPath("$.models[0].total_cost_micros").value(43000))
            .andExpect(jsonPath("$.models[1].model").value("nvidia/nemotron-3-super-120b-a12b"))
            .andExpect(jsonPath("$.models[2].model").value("nvidia/nemotron-3-ultra-550b-a55b"));
    }
}
