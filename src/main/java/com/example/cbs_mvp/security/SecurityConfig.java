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
@EnableWebSecurity(debug = false)
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // --- 公開エンドポイント（ホワイトリスト） ---
                        .requestMatchers("/", "/index.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/*.html", "/*.css", "/*.js", "/*.png", "/*.ico").permitAll()
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/ops/status").permitAll()
                        .requestMatchers("/ebay/webhook").permitAll() // 署名検証はController内で実施

                        // --- その他は全て認証必須（deny-by-default） ---
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtTokenService jwtTokenService) {
        return new JwtAuthFilter(jwtTokenService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
