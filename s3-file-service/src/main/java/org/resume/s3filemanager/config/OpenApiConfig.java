package org.resume.s3filemanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.properties.SwaggerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Конфигурация OpenAPI/Swagger для документации REST API.
 * <p>
 * Настраивает метаинформацию API: название, версию, описание и контакты.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SwaggerProperties.class)
public class OpenApiConfig {

    private final SwaggerProperties swaggerProperties;

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";
    private static final String API_TITLE = "CloudFileHub API";
    private static final String API_VERSION = "1.0";
    private static final String CONTACT_NAME = "Vladimir — GitHub";
    private static final String CONTACT_URL = "https://github.com/Vldr22/cloud-file-hub";
    private static final String DESCRIPTION_PATH = "/swagger-description.md";
    private static final String JWT_DESCRIPTION = "JWT токен, полученный через POST /api/auth/login";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(loadDescription())
                        .contact(new Contact()
                                .name(CONTACT_NAME)
                                .url(CONTACT_URL)))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(JWT_DESCRIPTION)))
                .tags(List.of(
                        new Tag().name("Home").description("Публичный доступ к списку файлов без аутентификации"),
                        new Tag().name("Auth").description("Регистрация, вход и выход из системы"),
                        new Tag().name("Files").description("Загрузка, скачивание и удаление файлов"),
                        new Tag().name("Admin").description("Управление пользователями, файлами и аудит-логами — только для администратора")
                ))
                .servers(buildServers());
    }

    private String loadDescription() {
        InputStream is = getClass().getResourceAsStream(DESCRIPTION_PATH);
        if (is == null) {
            return API_TITLE;
        }
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return API_TITLE;
        }
    }

    private List<Server> buildServers() {
        return swaggerProperties.getServers().stream()
                .map(s -> new Server().url(s.getUrl()).description(s.getDescription()))
                .toList();
    }
}