package com.duespilot.api.web;

import com.duespilot.api.service.CurrentUserService;
import com.duespilot.api.service.DemoDataService;
import com.duespilot.api.web.dto.DeleteDemoDataResponse;
import com.duespilot.api.web.dto.DemoDataPreviewResponse;
import com.duespilot.api.web.dto.SeedDemoDataRequest;
import com.duespilot.api.web.dto.SeedDemoDataResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/me/demo-data")
public class DemoDataController {

    private final DemoDataService demoDataService;
    private final CurrentUserService currentUserService;

    public DemoDataController(DemoDataService demoDataService, CurrentUserService currentUserService) {
        this.demoDataService = demoDataService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DemoDataPreviewResponse> getPreview() {
        CurrentUserService.UserContext ctx = currentUserService.getCurrentUser();
        DemoDataPreviewResponse preview = demoDataService.getPreview(ctx);
        return ResponseEntity.ok(preview);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SeedDemoDataResponse> seedDemoData(@RequestBody(required = false) SeedDemoDataRequest request) {
        CurrentUserService.UserContext ctx = currentUserService.getCurrentUser();
        SeedDemoDataResponse response = demoDataService.seedDemoData(ctx, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteDemoDataResponse> deleteDemoData() {
        CurrentUserService.UserContext ctx = currentUserService.getCurrentUser();
        DeleteDemoDataResponse response = demoDataService.deleteDemoData(ctx);
        return ResponseEntity.ok(response);
    }
}
