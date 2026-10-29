package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.service.DemoDataService;
import com.duespilot.api.web.dto.DeleteDemoDataResponse;
import com.duespilot.api.web.dto.DemoDataPreviewResponse;
import com.duespilot.api.web.dto.SeedDemoDataRequest;
import com.duespilot.api.web.dto.SeedDemoDataResponse;
import com.duespilot.api.web.error.ApiException;
import com.duespilot.api.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoDataController.class)
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("DemoDataController Web Tests (/api/v1/workspaces/me/demo-data)")
class DemoDataControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoDataService demoDataService;

    @MockitoBean
    private CurrentUserService currentUserService;

    private final UUID workspaceId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final CurrentUserService.UserContext authContext = new CurrentUserService.UserContext(
        "registered-user-1",
        "registered@msme.example",
        false,
        workspaceId
    );

    @Test
    @DisplayName("GET /api/v1/workspaces/me/demo-data returns real preview metrics")
    void testGetPreview() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.getPreview(authContext)).thenReturn(new DemoDataPreviewResponse(
            12, 20, 12, 28, false
        ));

        mockMvc.perform(get("/api/v1/workspaces/me/demo-data")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.caseCount").value(12))
            .andExpect(jsonPath("$.documentCount").value(20))
            .andExpect(jsonPath("$.contactCount").value(12))
            .andExpect(jsonPath("$.approximateStorageKb").value(28))
            .andExpect(jsonPath("$.alreadySeeded").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/me/demo-data rejects missing acknowledgement with 400 ACKNOWLEDGEMENT_REQUIRED")
    void testSeedMissingAcknowledgement() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.seedDemoData(eq(authContext), any())).thenThrow(
            new ApiException(HttpStatus.BAD_REQUEST, "ACKNOWLEDGEMENT_REQUIRED", "Explicit consent is required")
        );

        mockMvc.perform(post("/api/v1/workspaces/me/demo-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ACKNOWLEDGEMENT_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/me/demo-data rejects false acknowledgement with 400 ACKNOWLEDGEMENT_REQUIRED")
    void testSeedFalseAcknowledgement() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.seedDemoData(eq(authContext), any())).thenThrow(
            new ApiException(HttpStatus.BAD_REQUEST, "ACKNOWLEDGEMENT_REQUIRED", "Explicit consent is required")
        );

        mockMvc.perform(post("/api/v1/workspaces/me/demo-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ACKNOWLEDGEMENT_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/me/demo-data returns 409 ALREADY_SEEDED if demo data already exists")
    void testSeedAlreadySeededConflict() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.seedDemoData(eq(authContext), any())).thenThrow(
            new ApiException(HttpStatus.CONFLICT, "ALREADY_SEEDED", "Demo data already exists in this workspace")
        );

        mockMvc.perform(post("/api/v1/workspaces/me/demo-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_SEEDED"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/me/demo-data returns 201 CREATED on successful seed")
    void testSeedSuccess() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.seedDemoData(eq(authContext), any())).thenReturn(
            new SeedDemoDataResponse(true, 12, "Successfully added 12 synthetic demo cases to your workspace.")
        );

        mockMvc.perform(post("/api/v1/workspaces/me/demo-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledged\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.caseCount").value(12));
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/me/demo-data returns 200 OK with deleted count")
    void testDeleteDemoData() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(authContext);
        when(demoDataService.deleteDemoData(authContext)).thenReturn(
            new DeleteDemoDataResponse(true, 12, "Successfully removed 12 demo cases from your workspace.")
        );

        mockMvc.perform(delete("/api/v1/workspaces/me/demo-data")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.deletedCount").value(12));

        verify(demoDataService).deleteDemoData(authContext);
    }
}
