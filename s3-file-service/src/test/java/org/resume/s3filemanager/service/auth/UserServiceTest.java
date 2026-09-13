package org.resume.s3filemanager.service.auth;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserNotFoundException;
import org.resume.s3filemanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — управление пользователями")
class UserServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        username = FAKER.name().username();
        password = FAKER.internet().password();

        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(username);
        user.setPassword(FAKER.internet().password());
        user.setRole(UserRole.USER);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
        user.setStatus(UserStatus.ACTIVE);
    }

    /**
     * Создание пользователя — сохраняется с ролью USER и статусом NOT_UPLOADED.
     */
    @Test
    void shouldCreateUser_whenUsernameIsUnique() {
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(FAKER.internet().password());

        userService.createUser(username, password);

        verify(userRepository).save(any(User.class));
    }

    /**
     * Создание пользователя с занятым именем — UserAlreadyExistsException.
     */
    @Test
    void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
        when(userRepository.existsByUsername(username)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(username, password))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    /**
     * Поиск по имени — возвращает пользователя.
     */
    @Test
    void shouldReturnUser_whenUsernameExists() {
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        User result = userService.findByUsername(username);

        assertThat(result.getUsername()).isEqualTo(username);
    }

    /**
     * Поиск по имени — пользователь не найден, UserNotFoundException.
     */
    @Test
    void shouldThrowUserNotFoundException_whenUsernameNotFound() {
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByUsername(username))
                .isInstanceOf(UserNotFoundException.class);
    }

    /**
     * Поиск для аутентификации — возвращает пользователя.
     */
    @Test
    void shouldReturnUser_whenUsernameExistsForAuth() {
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        User result = userService.findByUsernameForAuth(username);

        assertThat(result.getUsername()).isEqualTo(username);
    }

    /**
     * Поиск для аутентификации — пользователь не найден, BadCredentialsException.
     */
    @Test
    void shouldThrowBadCredentialsException_whenUserNotFoundForAuth() {
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByUsernameForAuth(username))
                .isInstanceOf(BadCredentialsException.class);
    }

    /**
     * Обновление статуса загрузки — статус изменяется и пользователь сохраняется.
     */
    @Test
    void shouldUpdateUploadStatus_whenUserIsNotUnlimited() {
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);

        userService.updateUploadStatus(user, FileUploadStatus.FILE_UPLOADED);

        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.FILE_UPLOADED);
        verify(userRepository).save(user);
    }

    /**
     * Обновление статуса для UNLIMITED пользователя — статус не меняется.
     */
    @Test
    void shouldSkipStatusUpdate_whenUserIsUnlimited() {
        user.setUploadStatus(FileUploadStatus.UNLIMITED);

        userService.updateUploadStatus(user, FileUploadStatus.NOT_UPLOADED);

        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.UNLIMITED);
        verify(userRepository, never()).save(any());
    }

    /**
     * Постраничный список пользователей — возвращает результат из репозитория.
     */
    @Test
    void shouldReturnPage_whenFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(pageable)).thenReturn(page);

        Page<User> result = userService.findAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).findAll(pageable);
    }

    /**
     * Поиск по id — возвращает пользователя.
     */
    @Test
    void shouldReturnUser_whenIdExists() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = userService.findById(user.getId());

        assertThat(result).isEqualTo(user);
    }

    /**
     * Поиск по id — пользователь не найден, UserNotFoundException.
     */
    @Test
    void shouldThrowUserNotFoundException_whenIdNotFound() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(user.getId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    /**
     * Обновление UserStatus — статус изменяется и пользователь сохраняется.
     */
    @Test
    void shouldUpdateUserStatus_whenCalled() {
        userService.updateStatus(user, UserStatus.BLOCKED);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(userRepository).save(user);
    }

    /**
     * Обновление FileUploadStatus — статус изменяется и пользователь сохраняется.
     */
    @Test
    void shouldUpdateFileUploadStatus_whenCalled() {
        userService.updateStatus(user, FileUploadStatus.FILE_UPLOADED);

        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.FILE_UPLOADED);
        verify(userRepository).save(user);
    }

    /**
     * Удаление пользователя — репозиторий вызывает delete.
     */
    @Test
    void shouldDeleteUser_whenCalled() {
        userService.delete(user);

        verify(userRepository).delete(user);
    }
}