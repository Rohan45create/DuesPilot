package com.duespilot.api.web;

import com.duespilot.api.repository.FileBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StorageController.class)
@DisplayName("StorageController Tests: GET /api/v1/storage/report")
class StorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileBlobRepository fileBlobRepository;

    @Test
    @DisplayName("Returns blob storage usage and free-tier capacity percentage")
    void testGetStorageReport() throws Exception {
        when(fileBlobRepository.getTotalBlobCount()).thenReturn(42L);
        when(fileBlobRepository.getTotalStorageBytes()).thenReturn(5242880L); // 5 MB

        mockMvc.perform(get("/api/v1/storage/report").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_blobs").value(42))
            .andExpect(jsonPath("$.total_bytes").value(5242880))
            .andExpect(jsonPath("$.total_megabytes").value(5.0))
            .andExpect(jsonPath("$.free_tier_limit_bytes").value(StorageController.FREE_TIER_LIMIT_BYTES))
            .andExpect(jsonPath("$.free_tier_usage_percent").value(1.0));
    }
}
