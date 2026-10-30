package com.duespilot.api.service;

import com.duespilot.api.domain.SupplierCategory;
import com.duespilot.api.domain.UserEntity;
import com.duespilot.api.domain.WorkspaceEntity;
import com.duespilot.api.domain.WorkspaceMemberEntity;
import com.duespilot.api.repository.UserRepository;
import com.duespilot.api.repository.WorkspaceMemberRepository;
import com.duespilot.api.repository.WorkspaceRepository;
import com.duespilot.api.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
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
    private final ObjectProvider<DemoDataService> demoDataServiceProvider;

    public CurrentUserService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ObjectProvider<DemoDataService> demoDataServiceProvider) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.demoDataServiceProvider = demoDataServiceProvider;
    }

    public CurrentUserService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this(userRepository, workspaceRepository, workspaceMemberRepository, null);
    }

    public UserContext getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return fromJwt(jwt);
        }

        // F7 fix: fail closed with 401 when there is no valid JWT
        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "Authentication required. No valid JWT present."
        );
    }

    public UserContext fromJwt(Jwt jwt) {
        String sub = jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("sub");
        final String userId = (sub != null && !sub.isBlank()) ? sub : "anon-" + UUID.randomUUID();
        String email = jwt.getClaimAsString("email");
        boolean isAnonymous = isAnonymousToken(jwt);

        // F7 fix: Demo users get a personal, auto-seeded workspace instead of the shared read-only fallback
        Optional<UUID> existingWorkspaceId = workspaceMemberRepository.findWorkspaceIdByUserId(userId);
        UUID workspaceId = existingWorkspaceId.orElseGet(() -> getOrCreateWorkspaceForUser(userId, email, isAnonymous));

        return new UserContext(userId, email, isAnonymous, workspaceId);
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

    public UUID getOrCreateWorkspaceForUser(String userId, String email) {
        return getOrCreateWorkspaceForUser(userId, email, false);
    }

    @Transactional
    public synchronized UUID getOrCreateWorkspaceForUser(String userId, String email, boolean isDemo) {
        Optional<UUID> existing = workspaceMemberRepository.findWorkspaceIdByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        log.info("First sign-in for user {} (isDemo={}). Creating user and personal workspace.", userId, isDemo);

        // 1. Create User
        userRepository.save(new UserEntity(userId, email, Instant.now()));

        // 2. Create Personal Workspace
        String defaultName = (email != null && !email.isBlank())
            ? email.split("@")[0] + "'s Workspace"
            : (isDemo ? "Demo Workspace" : "Personal Workspace");

        UUID workspaceId = UUID.randomUUID();
        WorkspaceEntity workspace = new WorkspaceEntity(
            workspaceId,
            defaultName,
            SupplierCategory.MICRO,
            null,
            null,
            null,
            isDemo,
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

        // 4. Auto-seed demo workspace if isDemo
        if (isDemo && demoDataServiceProvider != null) {
            DemoDataService demoDataService = demoDataServiceProvider.getIfAvailable();
            if (demoDataService != null) {
                try {
                    demoDataService.seedWorkspaceForDemoUser(workspaceId, userId);
                } catch (Exception e) {
                    log.error("Failed to auto-seed demo workspace {}", workspaceId, e);
                }
            }
        }

        log.info("Created workspace {} for user {}", workspaceId, userId);
        return workspaceId;
    }
}
