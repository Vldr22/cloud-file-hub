package org.resume.s3filemanager.validation;

import jakarta.validation.ConstraintValidatorContext;
import lombok.experimental.UtilityClass;

/**
 * Утилита для регистрации кастомного сообщения об ошибке валидации.
 */
@UtilityClass
public class ConstraintValidatorUtils {

    public boolean addViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
        return false;
    }

}
