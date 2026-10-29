package com.duespilot.api.service;

import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.domain.UserEntity;
import com.duespilot.api.domain.WorkspaceEntity;
import com.duespilot.api.domain.WorkspaceMemberEntity;
import com.duespilot.api.repository.UserRepository;
import com.duespilot.api.repository.WorkspaceMemberRepository;
import com.duespilot.api.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    public static final UUID DEMO_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public record UserContext(
        String userId,
        String email,
        boolean isAnonymous,
        UUID workspaceId
    ) {}

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public CurrentUserService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public UserContext getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return fromJwt(jwt);
        }

        // Fallback for unauthenticated or internal calls
        return new UserContext("demo-user", "demo@duespilot.local", true, DEMO_WORKSPACE_ID);
    }

    public UserContext fromJwt(Jwt jwt) {
        String userId = jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        boolean isAnonymous = isAnonymousToken(jwt);

        if (isAnonymous) {
            return new UserContext(userId != null ? userId : "demo-user", email != null ? email : "demo@duespilot.local", true, DEMO_WORKSPACE_ID);
        }

        // Normal authenticated user: resolve their personal workspace
        Optional<UUID> existingWorkspaceId = workspaceMemberRepository.findWorkspaceIdByUserId(userId);
        UUID workspaceId = existingWorkspaceId.orElseGet(() -> getOrCreateWorkspaceForUser(userId, email));

        return new UserContext(userId, email, false, workspaceId);
    }

    public static boolean isAnonymousToken(Jwt jwt) {
        if (jwt == null) return false;
        Boolean isAnon = jwt.getClaimAsBoolean("is_anonymous");
        if (Boolean.TRUE.equals(isAnon)) {
            return true;
        }
        String email = jwt.getClaimAsString("email");
        return email == null || email.isBlank();
    }

    @Transactional
    public synchronized UUID getOrCreateWorkspaceForUser(String userId, String email) {
        Optional<UUID> existing = workspaceMemberRepository.findWorkspaceIdByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("First sign-in for user {}. Creating user and personal workspace.", userId);

        // 1. Create User
        userRepository.save(new UserEntity(userId, email, Instant.now()));

        // 2. Create Personal Workspace
        String defaultName = (email != null && !email.isBlank())
            ? email.split("@")[0] + "'s Workspace"
            : "Personal Workspace";

        UUID workspaceId = UUID.randomUUID();
        WorkspaceEntity workspace = new WorkspaceEntity(
            workspaceId,
            defaultName,
            SupplierCategory.MICRO,
            null,
            null,
            null,
            false,
            Instant.now(),
            0
        );
        workspaceRepository.save(workspace);

        // 3. Add Member as OWNER
        workspaceMemberRepository.save(new WorkspaceMemberEntity(
            UUID.randomUUID(),
            workspaceId,
            userId,
            "OWNER",
            Instant.now()
        ));

        log.info("Created workspace {} for user {}", workspaceId, userId);
        return workspaceId;
    }
}
