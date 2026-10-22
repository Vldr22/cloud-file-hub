package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.exception.FileAccessDeniedException;
import org.resume.s3filemanager.exception.FileUploadLimitException;
import org.resume.s3filemanager.exception.UserNotFoundException;
import org.resume.s3filemanager.repository.UserRepository;
import org.resume.s3filemanager.security.AuthenticatedUser;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FilePermissionService — проверка прав на загрузку и удаление файлов")
class FilePermissionServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FilePermissionService filePermissionService;

    private User user;
    private AuthenticatedUser authenticatedUser;

    @BeforeEach
    void setUp() {
        user = buildUser(UserRole.USER);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
        authenticatedUser = new AuthenticatedUser(user.getUsername(), UserRole.USER);
    }

    // checkUploadPermission

    /**
     * Пользователь со статусом NOT_UPLOADED получает разрешение на загрузку.
     */
    @Test
    void shouldAllowUpload_whenStatusIsNotUploaded() {
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        User result = filePermissionService.checkUploadPermission(authenticatedUser);

        assertThat(result).isEqualTo(user);
    }

    /**
     * Пользователь со статусом UNLIMITED загружает файлы без ограничений.
     */
    @Test
    void shouldAllowUpload_whenStatusIsUnlimited() {
        user.setUploadStatus(FileUploadStatus.UNLIMITED);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        User result = filePermissionService.checkUploadPermission(authenticatedUser);

        assertThat(result).isEqualTo(user);
    }

    /**
     * Пользователь со статусом FILE_UPLOADED получает FileUploadLimitException.
     */
    @Test
    void shouldThrowFileUploadLimitException_whenStatusIsFileUploaded() {
        user.setUploadStatus(FileUploadStatus.FILE_UPLOADED);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> filePermissionService.checkUploadPermission(authenticatedUser))
                .isInstanceOf(FileUploadLimitException.class);
    }

    /**
     * Пользователя нет в БД: UserNotFoundException.
     */
    @Test
    void shouldThrowUserNotFoundException_whenUserNotFoundInDatabase() {
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filePermissionService.checkUploadPermission(authenticatedUser))
                .isInstanceOf(UserNotFoundException.class);
    }

    // checkDeletePermission

    /**
     * Владелец может удалить свой файл без запроса в БД.
     */
    @Test
    void shouldAllowDelete_whenUserIsOwner() {
        FileMetadata file = buildFileMetadata(user);

        filePermissionService.checkDeletePermission(authenticatedUser, file);

        verifyNoInteractions(userRepository);
    }

    /**
     * Администратор может удалить чужой файл.
     */
    @Test
    void shouldAllowDelete_whenUserIsAdmin() {
        AuthenticatedUser admin = new AuthenticatedUser(FAKER.name().username(), UserRole.ADMIN);
        FileMetadata file = buildFileMetadata(buildUser(UserRole.USER));

        filePermissionService.checkDeletePermission(admin, file);

        verifyNoInteractions(userRepository);
    }

    /**
     * Обычный пользователь получает FileAccessDeniedException при удалении чужого файла.
     */
    @Test
    void shouldThrowFileAccessDeniedException_whenUserTriesToDeleteOthersFile() {
        FileMetadata file = buildFileMetadata(buildUser(UserRole.USER));

        assertThatThrownBy(() -> filePermissionService.checkDeletePermission(authenticatedUser, file))
                .isInstanceOf(FileAccessDeniedException.class);
    }

    /**
     * Администратор может удалить свой файл.
     */
    @Test
    void shouldAllowDelete_whenAdminDeletesOwnFile() {
        User adminUser = buildUser(UserRole.ADMIN);
        AuthenticatedUser admin = new AuthenticatedUser(adminUser.getUsername(), UserRole.ADMIN);
        FileMetadata file = buildFileMetadata(adminUser);

        filePermissionService.checkDeletePermission(admin, file);

        verifyNoInteractions(userRepository);
    }

    // markFileUploaded

    /**
     * Статус NOT_UPLOADED меняется на FILE_UPLOADED и пользователь сохраняется.
     */
    @Test
    void shouldMarkFileUploaded_whenStatusIsNotUploaded() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        filePermissionService.markFileUploaded(user.getId());

        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.FILE_UPLOADED);
        verify(userRepository).save(user);
    }

    /**
     * Статус UNLIMITED не меняется и сохранения нет.
     */
    @Test
    void shouldNotChangeStatus_whenStatusIsUnlimited() {
        user.setUploadStatus(FileUploadStatus.UNLIMITED);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        filePermissionService.markFileUploaded(user.getId());

        assertThat(user.getUploadStatus()).isEqualTo(FileUploadStatus.UNLIMITED);
        verify(userRepository, never()).save(any());
    }

    // === Helpers methods ===
    private User buildUser(UserRole role) {
        User owner = new User();
        owner.setId(FAKER.number().randomNumber());
        owner.setUsername(FAKER.name().username());
        owner.setRole(role);
        return owner;
    }

    private FileMetadata buildFileMetadata(User owner) {
        return FileMetadata.builder()
                .id(FAKER.number().randomNumber())
                .uniqueName(FAKER.internet().uuid())
                .originalName(FAKER.file().fileName())
                .fileHash(FAKER.crypto().md5())
                .size(FAKER.number().numberBetween(1024L, 10_000_000L))
                .user(owner)
                .build();
    }
}
