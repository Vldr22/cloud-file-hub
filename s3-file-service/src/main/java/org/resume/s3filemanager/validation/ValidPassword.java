package org.resume.s3filemanager.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.resume.s3filemanager.constant.ValidationMessages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Валидирует длину пароля в байтах UTF-8: BCrypt хеширует только первые 72 байта.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface ValidPassword {

    String message() default ValidationMessages.PASSWORD_BYTES_EXCEEDED;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
