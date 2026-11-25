package com.petcare.service;

import com.petcare.common.BusinessException;
import com.petcare.dto.AccountDTO;
import com.petcare.dto.PasswordChangeDTO;
import com.petcare.entity.User;
import com.petcare.mapper.UserMapper;
import com.petcare.util.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AuthService authService = new AuthService(
            userMapper, passwordEncoder, tokenService, redisTemplate);

    @Test
    void registerNeverLogsInExistingAccount() {
        User existing = new User();
        existing.setRole(0);
        existing.setStatus(1);
        when(userMapper.selectByUsername("demo")).thenReturn(existing);
        AccountDTO dto = new AccountDTO();
        dto.setUsername("demo");
        dto.setPassword("123456");

        assertThrows(BusinessException.class, () -> authService.register(dto));

        verify(tokenService, never()).createToken(org.mockito.ArgumentMatchers.any());
        verify(userMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passwordChangeIsBlockedAfterFailureLimit() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("auth:password:fail:2")).thenReturn("5");
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword("wrong-password");
        dto.setNewPassword("new-password");

        assertThrows(BusinessException.class, () -> authService.changePassword(2L, dto));

        verify(userMapper, never()).updatePassword(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(tokenService, never()).kickOut(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void successfulPasswordChangeClearsLoginFailureCounter() {
        User user = new User();
        user.setId(2L);
        user.setUsername("demo");
        user.setStatus(1);
        user.setPassword("bcrypt-current");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(passwordEncoder.matches("current-password", "bcrypt-current")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "bcrypt-current")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("bcrypt-new");
        when(userMapper.updatePassword(2L, "bcrypt-new")).thenReturn(1);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("auth:password:fail:2")).thenReturn(null);
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword("current-password");
        dto.setNewPassword("new-password");

        authService.changePassword(2L, dto);

        verify(redisTemplate).delete("auth:login:fail:ZGVtbw");
        verify(tokenService).kickOut(2L);
    }

    @Test
    void successfulRegistrationClearsPrecreatedFailureCounter() {
        AccountDTO dto = new AccountDTO();
        dto.setUsername("newuser");
        dto.setPassword("123456");
        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("bcrypt-new");
        when(tokenService.createToken(org.mockito.ArgumentMatchers.any())).thenReturn("token");

        authService.register(dto);

        verify(redisTemplate).delete("auth:login:fail:bmV3dXNlcg");
    }

    @Test
    void registrationReportsActionableErrorWhenTokenIssuanceFails() {
        AccountDTO dto = new AccountDTO();
        dto.setUsername("newuser2");
        dto.setPassword("123456");
        when(userMapper.selectByUsername("newuser2")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("bcrypt-new");
        when(tokenService.createToken(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("redis down"));

        BusinessException error = assertThrows(BusinessException.class, () -> authService.register(dto));

        assertEquals(500, error.getCode());
    }

    @Test
    void enablingUserClearsFailureCounter() {
        User user = new User();
        user.setId(2L);
        user.setUsername("demo");
        user.setRole(0);
        user.setStatus(0);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(userMapper.updateStatus(2L, 1)).thenReturn(1);

        authService.changeStatus(2L, 1);

        verify(redisTemplate).delete("auth:login:fail:ZGVtbw");
    }
}
