package com.logbook.logbookbackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oauth2SuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        "/api/auth/oauth2/authorize"
                );

        // Always show Google account chooser when starting OAuth
        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.additionalParameters(params -> params.put("prompt", "select_account")));

        return resolver;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — we use stateless JWT for API calls
                .csrf(AbstractHttpConfigurer::disable)

                // Sessions: IF_REQUIRED is critical — OAuth2 flow needs a session
                // temporarily to store the state/nonce during the Google redirect.
                // After the callback, we issue a JWT and the API becomes stateless.
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                // Public routes
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login/google",          // convenience redirect
                                "/api/auth/oauth2/authorize/**",   // kick off OAuth2
                                "/api/auth/oauth2/callback/**",    // Google callback
                                "/oauth2/**",                      // Spring OAuth2 internals
                                "/actuator/health"                 // Railway health check
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // OAuth2 login configuration
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(e ->
                                e.baseUri("/api/auth/oauth2/authorize")
                                        .authorizationRequestResolver(oauth2AuthorizationRequestResolver())
                        )
                        .redirectionEndpoint(e ->
                                e.baseUri("/api/auth/oauth2/callback/*")
                        )
                        .successHandler(oauth2SuccessHandler)
                )

                // Validate JWT on every API request (after OAuth2 callback, all calls use Bearer token)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}