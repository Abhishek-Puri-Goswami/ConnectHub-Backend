package com.connecthub.auth.config;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AdminSeeder adminSeeder;

    @Test
    void run_adminEmailAlreadyExists_skipsInsert() throws Exception {
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "pass");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(new User()));

        adminSeeder.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_adminUsernameTaken_skipsInsert() throws Exception {
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "pass");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));

        adminSeeder.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_newAdmin_savesAdminUser() throws Exception {
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "securePass!");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePass!")).thenReturn("$2a$12$hashedpassword");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        adminSeeder.run();

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        User saved = cap.getValue();
        assertEquals("admin@test.com", saved.getEmail());
        assertEquals("admin", saved.getUsername());
        assertEquals("ADMIN", saved.getRole());
        assertEquals("$2a$12$hashedpassword", saved.getPasswordHash());
        assertEquals(true, saved.isEmailVerified());
        assertEquals(true, saved.isActive());
    }
}
