package org.resume.s3filemanager.service.admin;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService — административные операции с пользователями")
class AdminUserServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private UserService userService;

    @Mock
    private JwtWhitelistService jwtWhitelistService;

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

    /**
     * Список пользователей: возвращает страницу с DTO.
     */
    @Test
    void shouldReturnUserPage_whenGetUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user)));

        Page<UserDetailsResponse> result = adminUserService.getUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().username()).isEqualTo(user.getUsername());
        assertThat(result.getContent().getFirst().role()).isEqualTo(UserRole.USER);
        verify(userService).findAll(pageable);
    }

    /**
     * Блокировка пользователя: статус меняется и JWT инвалидируется.
     */
    @Test
    void shouldBlockUserAndInvalidateToken_whenStatusIsBlocked() {
        when(userService.findById(user.getId())).thenReturn(user);

        adminUserService.changeStatus(user.getId(), UserStatus.BLOCKED);

        verify(userService).updateStatus(user, UserStatus.BLOCKED);
        verify(jwtWhitelistService).deleteToken(user.getUsername());
    }

    /**
     * Активация пользователя: статус меняется, JWT не трогаем.
     */
    @Test
    void shouldActivateUser_whenStatusIsActive() {
        when(userService.findById(user.getId())).thenReturn(user);

        adminUserService.changeStatus(user.getId(), UserStatus.ACTIVE);

        verify(userService).updateStatus(user, UserStatus.ACTIVE);
        verify(jwtWhitelistService, never()).deleteToken(anyString());
    }

    /**
     * Изменение статуса загрузки делегирует в UserService.
     */
    @Test
    void shouldChangeFileUploadStatus_whenCalled() {
        when(userService.findById(user.getId())).thenReturn(user);

        adminUserService.changeFileUploadStatus(user.getId(), FileUploadStatus.UNLIMITED);

        verify(userService).updateStatus(user, FileUploadStatus.UNLIMITED);
    }

    /**
     * Удаление пользователя: JWT инвалидируется и пользователь удаляется.
     */
    @Test
    void shouldDeleteUser_whenCalled() {
        when(userService.findById(user.getId())).thenReturn(user);

        adminUserService.deleteUser(user.getId());

        verify(jwtWhitelistService).deleteToken(user.getUsername());
        verify(userService).delete(user);
    }
}