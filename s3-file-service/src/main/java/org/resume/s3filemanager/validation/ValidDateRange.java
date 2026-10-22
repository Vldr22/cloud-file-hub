package org.resume.s3filemanager.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.resume.s3filemanager.constant.ValidationMessages;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Начало периода не позже его конца.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {

    String message() default ValidationMessages.INVALID_DATE_RANGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
