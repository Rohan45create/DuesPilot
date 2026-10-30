package com.duespilot.api.web;

import com.duespilot.api.config.SecurityHeadersFilter;
import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.domain.WorkspaceEntity;
import com.duespilot.api.repository.WorkspaceRepository;
import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import({ApiExceptionHandler.class, SecurityHeadersFilter.class})
@DisplayName("MeController Web Tests (GET & PATCH /api/v1/me)")
class MeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private WorkspaceRepository workspaceRepository;

    @Test
    @DisplayName("GET /api/v1/me: returns authenticated user profile, workspace, and flags")
    void testGetMeForAuthenticatedUser() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        CurrentUserService.UserContext context = new CurrentUserService.UserContext(
            "user-123",
            "owner@example.com",
            false,
            workspaceId
        );

        when(currentUserService.getCurrentUser()).thenReturn(context);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(
            new WorkspaceEntity(
                workspaceId,
                "Acme Supplies",
                SupplierCategory.MICRO,
                LocalDate.of(2022, 5, 10),
                "+919876543210",
                "+919876543210",
                false,
                Instant.now(),
                0
            )
        ));

        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value("user-123"))
            .andExpect(jsonPath("$.user.email").value("owner@example.com"))
            .andExpect(jsonPath("$.user.isAnonymous").value(false))
            .andExpect(jsonPath("$.workspace.id").value(workspaceId.toString()))
            .andExpect(jsonPath("$.workspace.businessName").value("Acme Supplies"))
            .andExpect(jsonPath("$.workspace.supplierSizeClass").value("MICRO"))
            .andExpect(jsonPath("$.flags.demoReadOnly").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/me: for anonymous user returns demo flags")
    void testGetMeForAnonymousUser() throws Exception {
        CurrentUserService.UserContext context = new CurrentUserService.UserContext(
            "anon-sub",
            null,
            true,
            CurrentUserService.DEMO_WORKSPACE_ID
        );

        when(currentUserService.getCurrentUser()).thenReturn(context);
        when(workspaceRepository.findById(CurrentUserService.DEMO_WORKSPACE_ID)).thenReturn(Optional.of(
            new WorkspaceEntity(
                CurrentUserService.DEMO_WORKSPACE_ID,
                "DuesPilot Demo Workspace",
                SupplierCategory.MICRO,
                null,
                null,
                null,
                true,
                Instant.now(),
                0
            )
        ));

        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.isAnonymous").value(true))
            .andExpect(jsonPath("$.flags.demoReadOnly").value(false))
            .andExpect(jsonPath("$.flags.canCreateCases").value(true))
            .andExpect(jsonPath("$.flags.requiresOnboarding").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/me: updates workspace onboarding profile")
    void testPatchMeUpdatesProfile() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        CurrentUserService.UserContext context = new CurrentUserService.UserContext(
            "user-123",
            "owner@example.com",
            false,
            workspaceId
        );

        when(currentUserService.getCurrentUser()).thenReturn(context);

        WorkspaceEntity existing = new WorkspaceEntity(
            workspaceId,
            "Old Name",
            SupplierCategory.UNKNOWN,
            null,
            null,
            null,
            false,
            Instant.now(),
            0
        );

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String updatePayload = """
            {
              "businessName": "New Enterprises",
              "supplierSizeClass": "SMALL",
              "udyamRegistrationDate": "2023-01-15",
              "ownerPhone": "+919999999999",
              "ownerWhatsapp": "+918888888888"
            }
            """;

        mockMvc.perform(patch("/api/v1/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspace.businessName").value("New Enterprises"))
            .andExpect(jsonPath("$.workspace.supplierSizeClass").value("SMALL"))
            .andExpect(jsonPath("$.workspace.udyamRegistrationDate").value("2023-01-15"))
            .andExpect(jsonPath("$.workspace.ownerPhone").value("+919999999999"))
            .andExpect(jsonPath("$.workspace.ownerWhatsapp").value("+918888888888"));

        verify(workspaceRepository).save(any());
    }
}
