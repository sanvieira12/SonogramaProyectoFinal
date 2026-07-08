package com.sonograma.controller;

import com.sonograma.service.CatalogCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/catalogo/admin")
@RequiredArgsConstructor
public class CatalogMaintenanceController {

    private final CatalogCleanupService catalogCleanupService;

    @PostMapping("/cleanup")
    public ResponseEntity<CatalogCleanupService.CatalogCleanupResult> cleanup(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        requireAdmin(authentication);
        boolean execute = body != null && Boolean.TRUE.equals(body.get("execute"));
        String scopeValue = body != null ? String.valueOf(body.getOrDefault("scope", "ALL_CATALOG")) : "ALL_CATALOG";
        CatalogCleanupService.CleanupScope scope = CatalogCleanupService.CleanupScope.from(scopeValue);
        return ResponseEntity.ok(execute
            ? catalogCleanupService.execute(scope)
            : catalogCleanupService.preview(scope));
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un usuario ADMIN puede limpiar el catálogo");
        }
    }
}
