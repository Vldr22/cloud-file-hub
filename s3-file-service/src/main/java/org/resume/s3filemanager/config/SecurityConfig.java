package org.resume.s3filemanager.config;

import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.properties.CorsProperties;
import org.resume.s3filemanager.security.JwtTokenFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Конфигурация Spring Security для защиты REST API.
 * <p>
 * Настраивает:
 * <ul>
 *   <li>JWT аутентификацию через cookie</li>
 *   <li>Stateless session management</li>
 *   <li>CORS политику из конфигурации</li>
 *   <li>Авторизацию на уровне endpoint'ов по ролям</li>
 * </ul>
 * Публичные endpoints: /api/auth/login, /api/auth/register, /api/home
 *
 * @see JwtTokenFilter
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final JwtTokenFilter jwtTokenFilter;
    private final CorsProperties corsProperties;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Настраивает цепочку фильтров безопасности.
     * <p>
     * Публичные пути:
     * <ul>
     *   <li>/api/auth/* - регистрация и вход</li>
     *   <li>/api/home - список файлов без аутентификации</li>
     * </ul>
     * Защищенные пути:
     * <ul>
     *   <li>/api/files/* - требуют аутентификации</li>
     *   <li>/api/files/multiple-upload - только ADMIN</li>
     *   <li>/api/admin/* - только ADMIN</li>
     * </ul>
     *
     * @param http конфигуратор HTTP безопасности
     * @return настроенная цепочка фильтров
     * @throws Exception при ошибке конфигурации
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/api/home").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/{uniqueName}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/files/upload").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/files/{uniqueName}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/files/multiple-upload").hasAuthority(UserRole.ADMIN.getAuthority())
                        .requestMatchers("/api/admin/**").hasAuthority(UserRole.ADMIN.getAuthority())
                        .anyRequest().authenticated())
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
