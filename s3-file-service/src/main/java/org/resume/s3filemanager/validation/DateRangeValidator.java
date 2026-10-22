package org.resume.s3filemanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.resume.s3filemanager.dto.AuditLogFilter;

/**
 * Проверяет период фильтра: ошибка привязывается к полю to, чтобы попасть в ответ с именем поля.
 */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, AuditLogFilter> {

    private static final String TO_FIELD = "to";

    @Override
    public boolean isValid(AuditLogFilter filter, ConstraintValidatorContext context) {
        if (filter == null || filter.from() == null || filter.to() == null) {
            return true;
        }

        if (!filter.from().isAfter(filter.to())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(TO_FIELD)
                .addConstraintViolation();
        return false;
    }
}
