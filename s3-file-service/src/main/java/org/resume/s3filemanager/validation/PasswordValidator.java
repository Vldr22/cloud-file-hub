package org.resume.s3filemanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Проверяет длину пароля в байтах UTF-8 под ограничение BCrypt.
 * null пропускается: за обязательность поля отвечает {@code @NotBlank}.
 */
@Component
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            return ConstraintValidatorUtils.addViolation(context,
                    String.format(ValidationMessages.PASSWORD_BYTES_EXCEEDED, BCRYPT_MAX_PASSWORD_BYTES));
        }

        return true;
    }

}
