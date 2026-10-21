package org.resume.s3filemanager.security;

import org.resume.s3filemanager.enums.UserRole;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Пользователь текущего запроса из claims JWT.
 */
public record AuthenticatedUser(
        String username,
        UserRole role
) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }

}
