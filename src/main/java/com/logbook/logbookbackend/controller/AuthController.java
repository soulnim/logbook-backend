package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    /**
     * GET /api/auth/me
     * Returns the currently authenticated user's profile.
     * Frontend calls this after storing the JWT to get user info.
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
                "createdAt", user.getCreatedAt().toString()
        ));
    }

    /**
     * GET /api/auth/login/google
     * Convenience redirect — frontend just navigates to this URL to kick off Google OAuth.
     * Spring Security handles the actual OAuth2 flow via /api/auth/oauth2/authorize/google
     */
    @GetMapping("/login/google")
    public ResponseEntity<Void> loginWithGoogle() {
        return ResponseEntity.status(302)
                .header("Location", "/api/auth/oauth2/authorize/google")
                .build();
    }
}