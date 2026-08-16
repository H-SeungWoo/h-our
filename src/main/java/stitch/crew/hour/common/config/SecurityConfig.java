package stitch.crew.hour.common.config;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import stitch.crew.hour.auth.service.OAuth2LoginSuccessHandler;
import stitch.crew.hour.common.config.entrypoint.JwtAccessDeniedHandler;
import stitch.crew.hour.common.config.entrypoint.JwtAuthenticationEntryPoint;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final ObjectProvider<OAuth2LoginSuccessHandler> oAuth2LoginSuccessHandlerProvider;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(formLogin -> formLogin.disable());

        // OAuth 설정이 없는 테스트 환경에서도 SecurityConfig가 안전하게 뜨도록 만듬
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth2 ->
                oAuth2LoginSuccessHandlerProvider.ifAvailable(oauth2::successHandler)
            );
        }

        return http
                .exceptionHandling(exp -> exp
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth ->auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/users/signup").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                    .requestMatchers("/api/addresses", "/api/addresses/**").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/lessons", "/api/lessons/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/reservations").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/lessons/**").authenticated()
                    .requestMatchers("/api/reservations", "/api/reservations/**").hasAnyRole( "USER")
                    .requestMatchers("/api/categories").hasAnyRole("ADMIN", "USER")
                    .requestMatchers("/api/lessons/**").hasAnyRole("ADMIN", "USER")
                    .requestMatchers("/api/reservations/**").hasAnyRole( "USER")
                    .requestMatchers("/api/lessons").hasAnyRole("ADMIN", "USER")
                    .requestMatchers("/api/admin/shppingpolicy").hasAnyRole("ADMIN", "USER")
                    .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        LinkedHashSet<String> allowedOrigins = new LinkedHashSet<>(List.of(
            "https://www.h-our.shop",
            "https://h-our.shop",
            "http://localhost:5173",
            "http://127.0.0.1:5173"
        ));

        List.of(frontendBaseUrl.split(",")).stream()
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .forEach(allowedOrigins::add);

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

}
