package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    /**
     * GET /api/auth/me
     * Returns the currently authenticated user's profile, including timezone.
     * Frontend calls this after storing the JWT to hydrate the user store.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return ResponseEntity.ok(Map.of(
                "id",        user.getId(),
                "email",     user.getEmail(),
                "name",      user.getName(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "timezone",  user.getTimezone() != null ? user.getTimezone() : "",
                "createdAt", user.getCreatedAt().toString()
        ));
    }

    /**
     * PATCH /api/auth/timezone
     * Body: { "timezone": "Asia/Kuala_Lumpur" }
     *
     * Two use-cases:
     *  1. First login — frontend sends the browser-detected timezone if user.timezone is blank.
     *  2. Settings page — user explicitly picks a timezone.
     */
    @PatchMapping("/timezone")
    public ResponseEntity<Map<String, Object>> updateTimezone(
            Authentication auth,
            @RequestBody Map<String, String> body) {

        String tz = body.get("timezone");
        if (tz == null || tz.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "timezone is required"));
        }

        // Validate that it's a real IANA zone before saving
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid timezone: " + tz));
        }

        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setTimezone(tz);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("timezone", tz));
    }

    /**
     * GET /api/auth/login/google
     * Convenience redirect — frontend navigates here to kick off Google OAuth.
     */
    @GetMapping("/login/google")
    public ResponseEntity<Void> loginWithGoogle() {
        return ResponseEntity.status(302)
                .header("Location", "/api/auth/oauth2/authorize/google")
                .build();
    }
}