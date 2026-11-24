package org.resume.s3filemanager.service.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.validation.PasswordValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordValidator - проверяет длину пароля в байтах UTF-8 под ограничение BCrypt")
class PasswordValidatorTest {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @InjectMocks
    private PasswordValidator passwordValidator;

    @Test
    @DisplayName("isValid - пропускает null, обязательность поля проверяет @NotBlank")
    void isValid_shouldReturnTrue_whenPasswordIsNull() {
        // given / when
        boolean result = passwordValidator.isValid(null, constraintValidatorContext);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(constraintValidatorContext);
    }

    @Test
    @DisplayName("isValid - пропускает ASCII-пароль ровно на 72 байта")
    void isValid_shouldReturnTrue_whenAsciiPasswordAtByteLimit() {
        // given: 72
        String password = "a".repeat(72);

        // when
        boolean result = passwordValidator.isValid(password, constraintValidatorContext);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(constraintValidatorContext);
    }

    @Test
    @DisplayName("isValid - отклоняет ASCII-пароль на 73 байта")
    void isValid_shouldReturnFalse_whenAsciiPasswordExceedsByteLimit() {
        // given
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);
        String password = "a".repeat(73);

        // when
        boolean result = passwordValidator.isValid(password, constraintValidatorContext);

        // then
        assertThat(result).isFalse();
        verify(constraintValidatorContext).disableDefaultConstraintViolation();
        verify(constraintValidatorContext).buildConstraintViolationWithTemplate(
                String.format(ValidationMessages.PASSWORD_BYTES_EXCEEDED, BCRYPT_MAX_PASSWORD_BYTES));
    }

    @Test
    @DisplayName("isValid - пропускает кириллический пароль ровно на 72 байта")
    void isValid_shouldReturnTrue_whenCyrillicPasswordAtByteLimit() {
        // given: 36 кириллических символов = 72 байта UTF-8
        String password = "я".repeat(36);

        // when
        boolean result = passwordValidator.isValid(password, constraintValidatorContext);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(constraintValidatorContext);
    }

    @Test
    @DisplayName("isValid - отклоняет кириллический пароль на 74 байта, хотя символов меньше 50")
    void isValid_shouldReturnFalse_whenCyrillicPasswordExceedsByteLimit() {
        // given: 37 кириллических символов = 74 байта UTF-8
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);
        String password = "я".repeat(37);

        // when
        boolean result = passwordValidator.isValid(password, constraintValidatorContext);

        // then
        assertThat(result).isFalse();
        verify(constraintValidatorContext).buildConstraintViolationWithTemplate(
                String.format(ValidationMessages.PASSWORD_BYTES_EXCEEDED, BCRYPT_MAX_PASSWORD_BYTES));
    }

}
