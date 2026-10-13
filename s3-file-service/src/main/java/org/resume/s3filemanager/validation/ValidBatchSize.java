package org.resume.s3filemanager.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.resume.s3filemanager.constant.ValidationMessages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Валидирует количество файлов в пакетной загрузке.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileBatchValidator.class)
public @interface ValidBatchSize {

    String message() default ValidationMessages.INVALID_BATCH_SIZE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
