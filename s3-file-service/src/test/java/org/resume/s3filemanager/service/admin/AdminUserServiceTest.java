package org.resume.s3filemanager.service.admin;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.mapper.UserMapper;
import org.resume.s3filemanager.mapper.UserMapperImpl;
import org.resume.s3filemanager.security.JwtWhitelistService;
import org.resume.s3filemanager.service.auth.UserService;
import org.resume.s3filemanager.service.file.YandexStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService - административные операции с пользователями")
class AdminUserServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private UserService userService;

    @Mock
    private JwtWhitelistService jwtWhitelistService;

    @Mock
    private YandexStorageService yandexStorageService;

    @Spy
    private UserMapper userMapper = new UserMapperImpl();

    @InjectMocks
    private AdminUserService adminUserService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
    }

    @Test
    @DisplayName("Возвращает страницу пользователей с DTO")
    void shouldReturnUserPage_whenGetUsers() {
        //given
        Pageable pageable = PageRequest.of(0, 10);
        when(userService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        //when
        Page<UserDetailsResponse> result = adminUserService.getUsers(pageable);

        //then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().username()).isEqualTo(user.getUsername());
        assertThat(result.getContent().getFirst().role()).isEqualTo(UserRole.USER);
        verify(userService).findAll(pageable);
    }

    @Test
    @DisplayName("Меняет статус на BLOCKED и инвалидирует JWT токен")
    void shouldBlockUserAndInvalidateToken_whenStatusIsBlocked() {
        //given
        when(userService.findById(user.getId())).thenReturn(user);

        //when
        adminUserService.changeStatus(user.getId(), UserStatus.BLOCKED);

        //then
        verify(userService).updateStatus(user, UserStatus.BLOCKED);
        verify(jwtWhitelistService).deleteToken(user.getUsername());
    }

    @Test
    @DisplayName("Меняет статус на ACTIVE и не трогает JWT токен")
    void shouldActivateUser_whenStatusIsActive() {
        //given
        when(userService.findById(user.getId())).thenReturn(user);

        //when
        adminUserService.changeStatus(user.getId(), UserStatus.ACTIVE);

        //then
        verify(userService).updateStatus(user, UserStatus.ACTIVE);
        verify(jwtWhitelistService, never()).deleteToken(anyString());
    }

    @Test
    @DisplayName("Меняет статус загрузки файлов пользователя")
    void shouldChangeFileUploadStatus_whenCalled() {
        //given
        when(userService.findById(user.getId())).thenReturn(user);

        //when
        adminUserService.changeFileUploadStatus(user.getId(), FileUploadStatus.UNLIMITED);

        //then
        verify(userService).overrideUploadStatus(user, FileUploadStatus.UNLIMITED);
    }

    @Test
    @DisplayName("Инвалидирует токен, удаляет пользователя с файлами из БД, затем объекты из S3")
    void shouldDeleteUserWithFiles_thenCleanupS3() {
        //given
        String firstKey = FAKER.internet().uuid() + ".pdf";
        String secondKey = FAKER.internet().uuid() + ".pdf";

        when(userService.findById(user.getId())).thenReturn(user);
        when(userService.deleteWithFiles(user)).thenReturn(List.of(firstKey, secondKey));

        //when
        adminUserService.deleteUser(user.getId());

        //then
        InOrder inOrder = inOrder(jwtWhitelistService, userService, yandexStorageService);
        inOrder.verify(jwtWhitelistService).deleteToken(user.getUsername());
        inOrder.verify(userService).deleteWithFiles(user);
        inOrder.verify(yandexStorageService).deleteFileYandexS3(firstKey);
        inOrder.verify(yandexStorageService).deleteFileYandexS3(secondKey);
    }

    @Test
    @DisplayName("Удаление пользователя остаётся в силе при сбое S3 на одном из файлов")
    void shouldKeepUserDeletion_whenS3CleanupFails() {
        //given
        String firstKey = FAKER.internet().uuid() + ".pdf";
        String secondKey = FAKER.internet().uuid() + ".pdf";

        when(userService.findById(user.getId())).thenReturn(user);
        when(userService.deleteWithFiles(user)).thenReturn(List.of(firstKey, secondKey));
        doThrow(new RuntimeException()).when(yandexStorageService).deleteFileYandexS3(firstKey);

        //when
        adminUserService.deleteUser(user.getId());

        //then
        verify(yandexStorageService).deleteFileYandexS3(secondKey);
    }

    @Test
    @DisplayName("Удаляет пользователя без файлов, S3 не вызывает")
    void shouldDeleteUserWithoutFiles_whenUserHasNoFiles() {
        //given
        when(userService.findById(user.getId())).thenReturn(user);
        when(userService.deleteWithFiles(user)).thenReturn(List.of());

        //when
        adminUserService.deleteUser(user.getId());

        //then
        verify(jwtWhitelistService).deleteToken(user.getUsername());
        verifyNoInteractions(yandexStorageService);
    }
}
