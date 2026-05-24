package com.connecthub.auth.service;

import com.connecthub.auth.config.JwtUtil;
import com.connecthub.auth.dto.*;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.exception.*;
import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AuthServiceImpl — handles all authentication business logic.
 *
 * Covers: registration with email OTP, password login, email/phone OTP login,
 * OAuth2 callback, session management (logout, token refresh, blacklist),
 * forgot/reset password, and user profile management.
 *
 * Transaction strategy:
 *   Every public method runs in a database transaction by default (@Transactional
 *   on the class). Read-only methods override with @Transactional(readOnly=true)
 *   so MySQL can use a cheaper read path.
 *
 * Token blacklist:
 *   On logout the access token is written to Redis ("token:blacklist:{token}")
 *   with a 24-hour TTL. The validateToken() endpoint checks this before
 *   confirming a token is valid — so logout takes effect immediately even if the
 *   JWT hasn't expired yet.
 *
 * Email enumeration protection:
 *   forgotPassword() always returns a generic message regardless of whether the
 *   email is registered. This stops an attacker from probing which emails exist.
 *
 * Password reset invalidation:
 *   After a password reset, a Redis key "user:invalidated:{userId}" is written
 *   so that any code checking old tokens can detect they are stale.
 *
 * OAuth2:
 *   oAuth2Callback() is intentionally unimplemented — OAuth2 is handled entirely
 *   by Spring Security. The method exists only to satisfy the AuthService interface.
 */
// Marks this as a Spring-managed service bean
@Service
// Lombok: generates a constructor that injects all final fields automatically
@RequiredArgsConstructor
// Lombok: creates a `log` field for SLF4J logging
@Slf4j
// Wraps every public method in a database transaction by default
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final StringRedisTemplate redis;
    private final EmailEventPublisher emailPublisher;
    private final UserProfileCacheService profileCache;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Redis key prefix for blacklisted tokens — value is "1", TTL = token lifetime
    private static final String TOKEN_BLACKLIST = "token:blacklist:";

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Creates a new account and sends a registration OTP to the user's email.
     * The account is saved with emailVerified=false — the user cannot log in until
     * they enter the OTP and confirm their email address.
     */
    @Override
    public ApiResponse<String> register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new DuplicateResourceException("Email already registered");
        if (userRepository.existsByUsername(req.getUsername()))
            throw new DuplicateResourceException("Username already taken");
        if (userRepository.existsByPhoneNumber(req.getPhoneNumber()))
            throw new DuplicateResourceException("Phone number already registered");

        User user = User.builder()
                .username(req.getUsername()).email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName()).phoneNumber(req.getPhoneNumber())
                .emailVerified(false).phoneVerified(false)
                .build();
        userRepository.save(user);

        String otp = otpService.generateAndStore("register", req.getEmail(), 5);
        otpService.setCooldown("register", req.getEmail(), 60);
        emailPublisher.sendOtpEmail(req.getEmail(), otp, "registration");
        log.info("User registered (pending verification): {}", req.getUsername());
        return ApiResponse.ok("Registration successful. OTP sent to your email.", req.getEmail());
    }

    /**
     * Verifies the registration OTP and marks the email as confirmed.
     * Returns JWT tokens immediately so the user is logged in right after verifying —
     * no separate login step needed.
     */
    @Override
    public AuthResponse verifyRegistrationOtp(OtpVerifyRequest req) {
        if (!otpService.verify("register", req.getEmail(), req.getOtp()))
            throw new BadRequestException("Invalid or expired OTP. Max 5 attempts allowed.");

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified for user: {}", user.getUsername());
        emailPublisher.sendWelcomeEmail(user.getEmail(), user.getUsername());
        return buildAuthResponse(user);
    }

    /**
     * Re-sends the registration OTP.
     * Enforces a cooldown — returns the remaining wait seconds if the user requests
     * too quickly. Rejects the request if the email is already verified.
     */
    @Override
    public ApiResponse<Void> resendRegistrationOtp(String email) {
        if (otpService.isOnCooldown("register", email)) {
            long remaining = otpService.getCooldownRemaining("register", email);
            return ApiResponse.<Void>builder().success(false)
                    .message("Please wait before requesting another OTP")
                    .cooldownSeconds((int) remaining).build();
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified())
            throw new BadRequestException("Email already verified");

        String otp = otpService.generateAndStore("register", email, 5);
        otpService.setCooldown("register", email, 60);
        emailPublisher.sendOtpEmail(email, otp, "registration");
        return ApiResponse.ok("OTP resent to your email");
    }

    // =========================================================================
    // PHONE OTP
    // =========================================================================

    /**
     * Sends an OTP to a phone number for verification or phone-based login.
     * The OTP is published to a Redis channel where notification-service picks it up
     * and forwards it to the SMS gateway.
     */
    @Override
    public ApiResponse<Void> requestPhoneOtp(PhoneOtpRequest request) {
        String phone = request.getPhoneNumber();
        if (otpService.isOnCooldown("phone", phone)) {
            long remaining = otpService.getCooldownRemaining("phone", phone);
            return ApiResponse.<Void>builder().success(false)
                    .message("Please wait before requesting another OTP")
                    .cooldownSeconds((int) remaining).build();
        }
        String otp = otpService.generateAndStore("phone", phone, 5);
        otpService.setCooldown("phone", phone, 45);
        emailPublisher.sendSmsOtp(phone, otp);
        return ApiResponse.ok("OTP sent to your phone number");
    }

    /**
     * Verifies the phone OTP and marks the number as confirmed on the user's account.
     */
    @Override
    public ApiResponse<Void> verifyPhoneOtp(PhoneOtpVerifyRequest request) {
        if (!otpService.verify("phone", request.getPhoneNumber(), request.getOtp()))
            throw new BadRequestException("Invalid or expired phone OTP");

        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(user -> {
            user.setPhoneVerified(true);
            userRepository.save(user);
            log.info("Phone verified for user: {}", user.getUsername());
        });

        return ApiResponse.ok("Phone number verified successfully");
    }

    // =========================================================================
    // LOGIN — EMAIL/USERNAME + PASSWORD
    // =========================================================================

    /**
     * Authenticates a user with username-or-email and password.
     * The identifier is tried as an email first, then as a username, so users can
     * log in with either. Rejects suspended accounts, unverified emails, and OAuth
     * accounts (which don't have a local password).
     */
    @Override
    public AuthResponse login(LoginRequest req) {
        User user = null;
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            user = userRepository.findByEmail(req.getEmail()).orElse(null);
            if (user == null) {
                user = userRepository.findByUsername(req.getEmail()).orElse(null);
            }
        }
        if (user == null && req.getUsername() != null && !req.getUsername().isBlank()) {
            user = userRepository.findByUsername(req.getUsername()).orElse(null);
        }
        if (user == null)
            throw new UnauthorizedException("Invalid credentials");

        if (!user.isActive())
            throw new UnauthorizedException("Account is suspended");
        if (!"LOCAL".equals(user.getProvider()))
            throw new UnauthorizedException("Please use " + user.getProvider() + " to sign in");
        if (!user.isEmailVerified())
            throw new UnauthorizedException("Email not verified. Please verify your email first.");

        if (req.getPassword() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new UnauthorizedException("Invalid credentials");

        log.info("User logged in (password): {}", user.getUsername());
        return buildAuthResponse(user);
    }

    // =========================================================================
    // LOGIN — EMAIL + OTP
    // =========================================================================

    /**
     * Sends a one-time login code to a verified email address.
     * Requires the account to exist, be active, and have a confirmed email.
     * A 45-second cooldown prevents repeated requests.
     */
    @Override
    public ApiResponse<Void> requestEmailLoginOtp(EmailLoginOtpRequest request) {
        String email = request.getEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        if (!user.isActive())
            throw new UnauthorizedException("Account is suspended");
        if (!user.isEmailVerified())
            throw new UnauthorizedException("Email not verified. Please verify your email first.");

        if (otpService.isOnCooldown("emaillogin", email)) {
            long remaining = otpService.getCooldownRemaining("emaillogin", email);
            return ApiResponse.<Void>builder().success(false)
                    .message("Please wait before requesting another OTP")
                    .cooldownSeconds((int) remaining).build();
        }

        String otp = otpService.generateAndStore("emaillogin", email, 5);
        otpService.setCooldown("emaillogin", email, 45);
        emailPublisher.sendOtpEmail(email, otp, "login");
        return ApiResponse.ok("Login OTP sent to your email");
    }

    /** Verifies the email login OTP and returns JWT tokens if correct. */
    @Override
    public AuthResponse loginWithEmailOtp(OtpVerifyRequest request) {
        if (!otpService.verify("emaillogin", request.getEmail(), request.getOtp()))
            throw new BadRequestException("Invalid or expired OTP");

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.isActive())
            throw new UnauthorizedException("Account is suspended");

        log.info("User logged in (email OTP): {}", user.getUsername());
        return buildAuthResponse(user);
    }

    // =========================================================================
    // LOGIN — PHONE + OTP
    // =========================================================================

    /**
     * Sends a login OTP via SMS to a registered phone number.
     * The phone must belong to an existing, active, verified account.
     */
    @Override
    public ApiResponse<Void> requestPhoneLoginOtp(PhoneOtpRequest request) {
        String phone = request.getPhoneNumber();
        User user = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this phone number"));
        if (!user.isActive())
            throw new UnauthorizedException("Account is suspended");
        if (!user.isPhoneVerified())
            throw new UnauthorizedException("Phone number not verified. Please verify your phone first.");

        if (otpService.isOnCooldown("phonelogin", phone)) {
            long remaining = otpService.getCooldownRemaining("phonelogin", phone);
            return ApiResponse.<Void>builder().success(false)
                    .message("Please wait before requesting another OTP")
                    .cooldownSeconds((int) remaining).build();
        }

        String otp = otpService.generateAndStore("phonelogin", phone, 5);
        otpService.setCooldown("phonelogin", phone, 45);
        emailPublisher.sendSmsOtp(phone, otp);
        return ApiResponse.ok("Login OTP sent to your phone");
    }

    /** Verifies the phone login OTP and returns JWT tokens if correct. */
    @Override
    public AuthResponse loginWithPhoneOtp(PhoneOtpVerifyRequest request) {
        if (!otpService.verify("phonelogin", request.getPhoneNumber(), request.getOtp()))
            throw new BadRequestException("Invalid or expired OTP");

        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.isActive())
            throw new UnauthorizedException("Account is suspended");

        log.info("User logged in (phone OTP): {}", user.getUsername());
        return buildAuthResponse(user);
    }

    // =========================================================================
    // SESSION MANAGEMENT
    // =========================================================================

    /**
     * Invalidates the access token by storing it in Redis with a 24-hour TTL.
     * Any subsequent request with this token will be rejected by validateToken().
     * The key expires on its own after 24 hours, matching the token's max lifetime.
     */
    @Override
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer "))
            token = token.substring(7);
        redis.opsForValue().set(TOKEN_BLACKLIST + token, "1", 24, TimeUnit.HOURS);
    }

    /**
     * Returns true only if the token passes both the blacklist check and signature/expiry check.
     * Used by the /auth/validate endpoint — the gateway can call this instead of
     * verifying locally if needed.
     */
    @Override
    public boolean validateToken(String token) {
        if (Boolean.TRUE.equals(redis.hasKey(TOKEN_BLACKLIST + token)))
            return false;
        return jwtUtil.isValid(token);
    }

    /**
     * Issues a new access token in exchange for a valid refresh token.
     * The new access token is built from the current database state, so any
     * role or subscription tier changes made since the last login are reflected.
     */
    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken))
            throw new UnauthorizedException("Invalid refresh token");
        User user = getUserById(jwtUtil.getUserId(refreshToken));
        return buildAuthResponse(user);
    }

    // =========================================================================
    // FORGOT PASSWORD
    // =========================================================================

    /**
     * Sends a password reset OTP to the given email.
     * Always returns the same generic message whether the email is registered or not —
     * this prevents attackers from discovering which emails have accounts here.
     * OAuth accounts are skipped because ConnectHub does not manage their passwords.
     */
    @Override
    public ApiResponse<Void> forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmail(req.getEmail()).ifPresent(user -> {
            if (!"LOCAL".equals(user.getProvider()))
                return;
            if (!otpService.isOnCooldown("reset", req.getEmail())) {
                String otp = otpService.generateAndStore("reset", req.getEmail(), 10);
                otpService.setCooldown("reset", req.getEmail(), 60);
                emailPublisher.sendOtpEmail(req.getEmail(), otp, "password_reset");
            }
        });
        return ApiResponse.ok("If an account with this email exists, we have sent a reset code.");
    }

    /**
     * Verifies the reset OTP and returns a short-lived reset token.
     * The reset token is a JWT with purpose="PASSWORD_RESET" — the resetPassword()
     * endpoint checks this claim before allowing the change. Using a JWT here means
     * no server-side session state needs to be stored.
     */
    @Override
    public ApiResponse<String> verifyResetOtp(OtpVerifyRequest req) {
        if (!otpService.verify("reset", req.getEmail(), req.getOtp()))
            throw new BadRequestException("Invalid or expired OTP");
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String resetToken = jwtUtil.generateResetToken(user.getUserId());
        return ApiResponse.ok("OTP verified. Use the reset token to set a new password.", resetToken);
    }

    /**
     * Sets a new password using the reset token issued by verifyResetOtp().
     * Checks the token's signature, expiry, and purpose claim before accepting the change.
     * After resetting, writes a Redis key so that any code checking old tokens knows they
     * are now stale.
     */
    @Override
    public ApiResponse<Void> resetPassword(ResetPasswordRequest req) {
        if (!jwtUtil.isValid(req.getResetToken()))
            throw new UnauthorizedException("Invalid or expired reset token");
        var claims = jwtUtil.parseToken(req.getResetToken());
        if (!"PASSWORD_RESET".equals(claims.get("purpose")))
            throw new UnauthorizedException("Invalid token purpose");

        int userId = Integer.parseInt(claims.getSubject());
        User user = getUserById(userId);
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Mark this user's sessions as invalidated — TTL = 7 days to cover any refresh tokens still alive
        redis.opsForValue().set("user:invalidated:" + userId, String.valueOf(System.currentTimeMillis()), 7,
                TimeUnit.DAYS);
        log.info("Password reset completed for user: {}", user.getUsername());
        return ApiResponse.ok("Password reset successful. Please login with your new password.");
    }

    // =========================================================================
    // OAUTH2
    // =========================================================================

    /**
     * Not used. OAuth2 login is handled by Spring Security via OAuth2AuthenticationSuccessHandler.
     * This method exists only because the AuthService interface declares it.
     */
    @Override
    public AuthResponse oAuth2Callback(String provider, OAuth2CallbackRequest req) {
        throw new BadRequestException("Use /oauth2/authorization/" + provider + " for OAuth2 login");
    }

    // =========================================================================
    // USER MANAGEMENT
    // =========================================================================

    @Override
    // Read-only transaction — no DB writes happen here, allows MySQL to use a faster read path
    @Transactional(readOnly = true)
    public User getUserById(int userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    /**
     * Returns a user's public profile, served from Redis cache when available.
     * On a cache miss the profile is loaded from MySQL and then cached for next time.
     */
    @Override
    public UserProfileDto getPublicProfile(int userId) {
        UserProfileDto cached = profileCache.getCachedProfile(userId);
        if (cached != null) {
            log.debug("Cache HIT user:profile:{}", userId);
            return cached;
        }
        log.debug("Cache MISS user:profile:{} — loading from DB", userId);
        UserProfileDto profile = toProfileDto(getUserById(userId));
        profileCache.cacheProfile(userId, profile);
        return profile;
    }

    /**
     * Updates editable profile fields and clears the Redis cache entry.
     * Username uniqueness is only checked when the value actually changes.
     */
    @Override
    public UserProfileDto updateProfile(int userId, UpdateProfileRequest req) {
        User user = getUserById(userId);
        if (req.getFullName() != null)
            user.setFullName(req.getFullName());
        if (req.getUsername() != null && !user.getUsername().equals(req.getUsername())) {
            if (userRepository.existsByUsername(req.getUsername()))
                throw new DuplicateResourceException("Username already taken");
            user.setUsername(req.getUsername());
        }
        if (req.getAvatarUrl() != null)
            user.setAvatarUrl(req.getAvatarUrl());
        if (req.getBio() != null)
            user.setBio(req.getBio());
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(req.getPhoneNumber()))
                throw new DuplicateResourceException("Phone number already registered");
            user.setPhoneNumber(req.getPhoneNumber());
        }
        UserProfileDto updated = toProfileDto(userRepository.save(user));
        profileCache.evict(userId);
        return updated;
    }

    /**
     * Changes the password after verifying the current one.
     * OAuth users are rejected because ConnectHub does not manage their credentials.
     */
    @Override
    public void changePassword(int userId, ChangePasswordRequest req) {
        User user = getUserById(userId);
        if (!"LOCAL".equals(user.getProvider()))
            throw new BadRequestException("OAuth users cannot change password");
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
            throw new UnauthorizedException("Current password is incorrect");
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true) // search only reads data — no writes
    public List<UserProfileDto> searchUsers(String query) {
        return userRepository.searchUsers(query).stream().map(this::toProfileDto).toList();
    }

    /**
     * Sets the user's presence status (ONLINE, AWAY, DND, INVISIBLE).
     * Clears the profile cache so other services see the updated status on next fetch.
     */
    @Override
    public void updateStatus(int userId, String status) {
        if (!List.of("ONLINE", "AWAY", "DND", "INVISIBLE").contains(status))
            throw new BadRequestException("Invalid status: " + status);
        User user = getUserById(userId);
        user.setStatus(status);
        userRepository.save(user);
        profileCache.evict(userId);
    }

    /** Called when a user disconnects from WebSocket — records the time and sets status to OFFLINE. */
    @Override
    public void updateLastSeen(int userId) {
        User user = getUserById(userId);
        user.setLastSeenAt(LocalDateTime.now());
        user.setStatus("OFFLINE");
        userRepository.save(user);
    }

    @Override
    public User suspendUser(int userId) {
        User u = getUserById(userId);
        u.setActive(false);
        User saved = userRepository.save(u);
        profileCache.evict(userId);
        return saved;
    }

    @Override
    public User reactivateUser(int userId) {
        User u = getUserById(userId);
        u.setActive(true);
        User saved = userRepository.save(u);
        profileCache.evict(userId);
        return saved;
    }

    @Override
    public User changeRole(int userId, String role) {
        User u = getUserById(userId);
        String normalized = role.toUpperCase();
        u.setRole(normalized);
        if ("PLATFORM_ADMIN".equals(normalized)) {
            u.setSubscriptionTier("PLATINUM");
        } else if ("ADMIN".equals(normalized)) {
            u.setSubscriptionTier("PREMIUM");
        } else if ("USER".equals(normalized)) {
            u.setSubscriptionTier("FREE");
        }
        User saved = userRepository.save(u);
        profileCache.evict(userId);
        return saved;
    }

    @Override
    public void deleteUser(int userId) {
        userRepository.deleteById(userId);
        log.info("Publishing USER_DELETED event for userId: {}", userId);
        kafkaTemplate.send("auth.user.deleted", String.valueOf(userId));
    }

    /**
     * User-initiated account deletion.
     * LOCAL accounts must confirm their password to prevent accidental or CSRF-driven
     * deletions. OAuth accounts skip the password check because they have no local password.
     */
    @Override
    public void selfDeleteAccount(int userId, String password) {
        User user = getUserById(userId);
        if ("LOCAL".equals(user.getProvider())) {
            if (password == null || password.isBlank()) {
                throw new RuntimeException("Password confirmation is required to delete your account");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new RuntimeException("Incorrect password");
            }
        }
        // Invalidate existing tokens so they are rejected immediately after deletion
        redis.opsForValue().set("user:invalidated:" + userId, String.valueOf(System.currentTimeMillis()),
                jwtUtil.getAccessExpiry() / 1000, java.util.concurrent.TimeUnit.SECONDS);
        // Remove the user from the DB and fire a Kafka event so other services can clean up
        deleteUser(userId);
        profileCache.evict(userId);
        log.info("User {} self-deleted their account", userId);
    }

    @Override
    @Transactional(readOnly = true) // admin list — read only
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true) // batch profile fetch — read only
    public List<UserProfileDto> getUsersByIds(List<Integer> ids) {
        return userRepository.findByUserIdIn(ids).stream().map(this::toProfileDto).toList();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Resolves the effective subscription tier for a user, accounting for role overrides.
     * PLATFORM_ADMIN always returns PLATINUM; ADMIN returns at least PREMIUM.
     * For regular users it falls back to the stored tier, defaulting to FREE if null.
     */
    private String resolvedTier(User u) {
        String role = u.getRole() != null ? u.getRole().toUpperCase() : "USER";
        if ("PLATFORM_ADMIN".equals(role)) return "PLATINUM";
        if ("ADMIN".equals(role)) {
            String t = u.getSubscriptionTier();
            return (t != null && !"FREE".equals(t)) ? t : "PREMIUM";
        }
        return u.getSubscriptionTier() != null ? u.getSubscriptionTier() : "FREE";
    }

    /** Converts a User entity into the public-facing DTO that is returned to callers. */
    private UserProfileDto toProfileDto(User u) {
        return UserProfileDto.builder()
                .userId(u.getUserId()).username(u.getUsername()).email(u.getEmail())
                .fullName(u.getFullName()).phoneNumber(u.getPhoneNumber())
                .avatarUrl(u.getAvatarUrl()).bio(u.getBio()).status(u.getStatus())
                .role(u.getRole())
                .subscriptionTier(resolvedTier(u))
                .emailVerified(u.isEmailVerified())
                .phoneVerified(u.isPhoneVerified()).lastSeenAt(u.getLastSeenAt())
                .createdAt(u.getCreatedAt()).build();
    }

    /**
     * Builds the standard login response returned by every successful login or verify call.
     * Contains access token, refresh token, expiry, and a slim user DTO.
     */
    private AuthResponse buildAuthResponse(User user) {
        // Remove any mid-session tier override key — the new JWT carries the correct tier directly
        redis.delete("sub:tier:" + user.getUserId());

        String accessToken = jwtUtil.generateAccessToken(user);

        // Register this session in Redis so it appears in the active session list.
        // The jti (unique token ID) is the key; TTL matches the token lifetime so it self-cleans.
        try {
            String jti = jwtUtil.extractClaim(accessToken, c -> c.get("jti", String.class));
            if (jti != null) {
                long ttlSeconds = jwtUtil.getAccessExpiry() / 1000;
                String metadata = "{\"loginAt\":\"" + java.time.Instant.now() + "\",\"userId\":" + user.getUserId()
                        + "}";
                redis.opsForValue().set("session:" + user.getUserId() + ":" + jti, metadata,
                        ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to register session in Redis: {}", e.getMessage());
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(jwtUtil.generateRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessExpiry() / 1000)
                .user(AuthResponse.UserDto.builder()
                        .userId(user.getUserId()).username(user.getUsername()).email(user.getEmail())
                        .fullName(user.getFullName()).avatarUrl(user.getAvatarUrl())
                        .phoneNumber(user.getPhoneNumber())
                        .status(user.getStatus()).role(user.getRole())
                        .subscriptionTier(resolvedTier(user))
                        .active(user.isActive())
                        .build())
                .build();
    }
}
