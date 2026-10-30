package com.duespilot.api.web;

import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.domain.WorkspaceEntity;
import com.duespilot.api.repository.WorkspaceRepository;
import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.web.dto.FlagsResponse;
import com.duespilot.api.web.dto.MeResponse;
import com.duespilot.api.web.dto.UpdateWorkspaceProfileRequest;
import com.duespilot.api.web.dto.UserProfileResponse;
import com.duespilot.api.web.dto.WorkspaceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserService currentUserService;
    private final WorkspaceRepository workspaceRepository;

    public MeController(CurrentUserService currentUserService, WorkspaceRepository workspaceRepository) {
        this.currentUserService = currentUserService;
        this.workspaceRepository = workspaceRepository;
    }

    @GetMapping
    public ResponseEntity<MeResponse> getProfile() {
        CurrentUserService.UserContext ctx = currentUserService.getCurrentUser();

        WorkspaceEntity workspace = workspaceRepository.findById(ctx.workspaceId())
            .orElseGet(() -> new WorkspaceEntity(
                ctx.workspaceId(),
                "DuesPilot Demo MSME Workspace",
                SupplierCategory.MICRO,
                null,
                null,
                null,
                true,
                Instant.now(),
                0
            ));

        UserProfileResponse user = new UserProfileResponse(ctx.userId(), ctx.email(), ctx.isAnonymous());
        WorkspaceResponse ws = WorkspaceResponse.fromEntity(workspace);
        boolean requiresOnboarding = !ctx.isAnonymous() && (
            workspace.supplierSizeClass() == SupplierCategory.UNKNOWN ||
            workspace.name() == null ||
            workspace.name().isBlank()
        );
        FlagsResponse flags = new FlagsResponse(false, true, requiresOnboarding);

        return ResponseEntity.ok(new MeResponse(user, ws, flags));
    }

    @PatchMapping
    public ResponseEntity<MeResponse> updateProfile(@RequestBody UpdateWorkspaceProfileRequest request) {
        CurrentUserService.UserContext ctx = currentUserService.getCurrentUser();

        WorkspaceEntity existing = workspaceRepository.findById(ctx.workspaceId())
            .orElseThrow(() -> new IllegalStateException("Workspace not found for user: " + ctx.userId()));

        String newName = (request.businessName() != null && !request.businessName().isBlank())
            ? request.businessName().trim()
            : existing.name();

        SupplierCategory newSize = request.supplierSizeClass() != null
            ? SupplierCategory.fromString(request.supplierSizeClass())
            : existing.supplierSizeClass();

        WorkspaceEntity updated = new WorkspaceEntity(
            existing.id(),
            newName,
            newSize,
            request.udyamRegistrationDate() != null ? request.udyamRegistrationDate() : existing.udyamRegistrationDate(),
            request.ownerPhone() != null ? request.ownerPhone().trim() : existing.ownerPhone(),
            request.ownerWhatsapp() != null ? request.ownerWhatsapp().trim() : existing.ownerWhatsapp(),
            existing.isDemo(),
            existing.createdAt(),
            existing.version()
        );

        WorkspaceEntity saved = workspaceRepository.save(updated);

        UserProfileResponse user = new UserProfileResponse(ctx.userId(), ctx.email(), false);
        WorkspaceResponse ws = WorkspaceResponse.fromEntity(saved);
        boolean requiresOnboarding = saved.supplierSizeClass() == SupplierCategory.UNKNOWN;
        FlagsResponse flags = new FlagsResponse(false, true, requiresOnboarding);

        return ResponseEntity.ok(new MeResponse(user, ws, flags));
    }
}
