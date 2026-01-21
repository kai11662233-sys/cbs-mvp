package com.example.cbs_mvp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公開エンドポイント
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/change-password").authenticated() // 認証必須
                        .requestMatchers("/ops/status").permitAll()
                        .requestMatchers("/pricing/**").permitAll()
                        .requestMatchers("/ebay/webhook").permitAll()

                        // OPS-KEY or JWT 認証が必要
                        .requestMatchers("/ops/**").authenticated()
                        .requestMatchers("/cash/**").authenticated()
                        .requestMatchers("/candidates/**").authenticated()
                        .requestMatchers("/orders/**").authenticated()
                        .requestMatchers("/drafts/**").authenticated()
                        .requestMatchers("/procurement/**").authenticated()
                        .requestMatchers("/threepl/**").authenticated()
                        .requestMatchers("/fx/**").authenticated()

                        // その他は認証必須
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
