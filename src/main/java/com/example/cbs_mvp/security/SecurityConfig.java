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
                        // 公開エンドポイント
                        .requestMatchers("/", "/index.html").permitAll()
                        .requestMatchers("/error").permitAll() // エラー詳細が見えるようにする
                        .requestMatchers("/*.html", "/*.css", "/*.js", "/*.png", "/*.ico").permitAll()
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/change-password").authenticated() // 認証必須
                        .requestMatchers("/ops/status").permitAll()
                        .requestMatchers("/pricing/**").permitAll()
                        .requestMatchers("/fx/rate").permitAll() // Price calc needs this
                        .requestMatchers("/ebay/webhook").permitAll()
                        .requestMatchers("/discovery/**").permitAll() // OPS-KEY認証はControllerで実施

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
                        // .anyRequest().authenticated())
                        .anyRequest().permitAll())
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
