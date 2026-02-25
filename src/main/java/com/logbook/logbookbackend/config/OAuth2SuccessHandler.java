package com.logbook.logbookbackend.config;

import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String googleId  = oauthUser.getAttribute("sub");
        String email     = oauthUser.getAttribute("email");
        String name      = oauthUser.getAttribute("name");
        String avatarUrl = oauthUser.getAttribute("picture");

        // Upsert user
        User user = userRepository.findByGoogleId(googleId)
                .map(existing -> {
                    existing.setName(name);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setLastLogin(OffsetDateTime.now());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("New user signing up: {}", email);
                    return userRepository.save(User.builder()
                            .googleId(googleId)
                            .email(email)
                            .name(name)
                            .avatarUrl(avatarUrl)
                            .build());
                });

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        log.info("OAuth2 login success for userId={}", user.getId());

        // Redirect to frontend with token in query param
        // Frontend should grab it, store in memory/localStorage, then clean the URL
        String redirectUrl = frontendUrl + "/auth/callback?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}