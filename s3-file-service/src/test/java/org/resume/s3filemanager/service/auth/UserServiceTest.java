package org.resume.s3filemanager.service.auth;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserNotFoundException;
import org.resume.s3filemanager.repository.FileMetadataRepository;
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
@DisplayName("UserService - управление пользователями")
class UserServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

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

    @Test
    @DisplayName("Сохраняет нового пользователя, когда имя уникально")
    void shouldCreateUser_whenUsernameIsUnique() {
        //given
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(FAKER.internet().password());

        //when
        userService.createUser(username, password);

        //then
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Бросает UserAlreadyExistsException, когда имя уже занято")
    void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
        //given
        when(userRepository.existsByUsername(username)).thenReturn(true);

        //when //then
        assertThatThrownBy(() -> userService.createUser(username, password))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Возвращает пользователя при поиске по существующему имени")
    void shouldReturnUser_whenUsernameExists() {
        //given
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        //when
        User result = userService.findByUsername(username);

        //then
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("Бросает UserNotFoundException при поиске по несуществующему имени")
    void shouldThrowUserNotFoundException_whenUsernameNotFound() {
        //given
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        //when //then
        assertThatThrownBy(() -> userService.findByUsername(username))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Возвращает пользователя при поиске для аутентификации")
    void shouldReturnUser_whenUsernameExistsForAuth() {
        //given
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        //when
        User result = userService.findByUsernameForAuth(username);

        //then
        assertThat(result.getUsername()).isEqualTo(username);
    }

    @Test
    @DisplayName("Бросает BadCredentialsException при аутентификации ненайденного пользователя")
    void shouldThrowBadCredentialsException_whenUserNotFoundForAuth() {
        //given
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        //when //then
        assertThatThrownBy(() -> userService.findByUsernameForAuth(username))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Обновляет статус загрузки и сохраняет пользователя без UNLIMITED")
    void shouldUpdateUploadStatus_whenUserIsNotUnlimited() {
        //given
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);

        //when
        userService.updateUploadStatus(user, FileUploadStatus.FILE_UPLOADED);

        //then
        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.FILE_UPLOADED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Не меняет статус загрузки у пользователя с UNLIMITED")
    void shouldSkipStatusUpdate_whenUserIsUnlimited() {
        //given
        user.setUploadStatus(FileUploadStatus.UNLIMITED);

        //when
        userService.updateUploadStatus(user, FileUploadStatus.NOT_UPLOADED);

        //then
        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.UNLIMITED);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Возвращает страницу пользователей из репозитория")
    void shouldReturnPage_whenFindAll() {
        //given
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAllByOrderByIdAsc(pageable)).thenReturn(page);

        //when
        Page<User> result = userService.findAll(pageable);

        //then
        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).findAllByOrderByIdAsc(pageable);
    }

    @Test
    @DisplayName("Возвращает пользователя при поиске по существующему id")
    void shouldReturnUser_whenIdExists() {
        //given
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        //when
        User result = userService.findById(user.getId());

        //then
        assertThat(result).isEqualTo(user);
    }

    @Test
    @DisplayName("Бросает UserNotFoundException при поиске по несуществующему id")
    void shouldThrowUserNotFoundException_whenIdNotFound() {
        //given
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        //when //then
        assertThatThrownBy(() -> userService.findById(user.getId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Меняет UserStatus и сохраняет пользователя")
    void shouldUpdateUserStatus_whenCalled() {
        //when
        userService.updateStatus(user, UserStatus.BLOCKED);

        //then
        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Меняет FileUploadStatus и сохраняет пользователя")
    void shouldUpdateFileUploadStatus_whenCalled() {
        //when
        userService.updateStatus(user, FileUploadStatus.FILE_UPLOADED);

        //then
        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.FILE_UPLOADED);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Удаляет файлы и пользователя и возвращает S3-ключи файлов")
    void shouldDeleteFilesAndUser_whenDeleteWithFiles() {
        //given
        FileMetadata first = new FileMetadata();
        first.setUniqueName(FAKER.internet().uuid() + ".pdf");
        FileMetadata second = new FileMetadata();
        second.setUniqueName(FAKER.internet().uuid() + ".pdf");

        when(fileMetadataRepository.findAllByUserId(user.getId())).thenReturn(List.of(first, second));

        //when
        List<String> s3Keys = userService.deleteWithFiles(user);

        //then
        assertThat(s3Keys).containsExactly(first.getUniqueName(), second.getUniqueName());
        InOrder inOrder = inOrder(fileMetadataRepository, userRepository);
        inOrder.verify(fileMetadataRepository).deleteAll(List.of(first, second));
        inOrder.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("Возвращает пустой список S3-ключей при удалении пользователя без файлов")
    void shouldReturnEmptyKeys_whenUserHasNoFiles() {
        //given
        when(fileMetadataRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        //when
        List<String> s3Keys = userService.deleteWithFiles(user);

        //then
        assertThat(s3Keys).isEmpty();
        verify(userRepository).delete(user);
    }
}
