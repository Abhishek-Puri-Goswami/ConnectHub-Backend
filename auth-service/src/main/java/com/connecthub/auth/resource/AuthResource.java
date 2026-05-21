package com.connecthub.auth.resource;

import com.connecthub.auth.config.IpRateLimiter;
import com.connecthub.auth.dto.*;
import com.connecthub.auth.entity.User;
import com.connecthub.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, OTP verification, password management")
public class AuthResource {

    private final AuthService authService;
    private final IpRateLimiter ipRateLimiter;
    private final StringRedisTemplate redis;

    // ─── Registration ─────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-registration-otp")
    @Operation(summary = "Verify registration OTP")
    public ResponseEntity<AuthResponse> verifyRegistrationOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyRegistrationOtp(request));
    }

    @PostMapping("/resend-registration-otp")
    @Operation(summary = "Resend registration OTP")
    public ResponseEntity<ApiResponse<Void>> resendRegistrationOtp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.resendRegistrationOtp(body.get("email")));
    }

    // ─── Phone OTP (registration verification) ───────────────────────

    @PostMapping("/phone/request-otp")
    @Operation(summary = "Request phone OTP", description = "Sends OTP via SMS for phone verification during registration")
    public ResponseEntity<ApiResponse<Void>> requestPhoneOtp(@Valid @RequestBody PhoneOtpRequest request, HttpServletRequest httpReq) {
        if (!ipRateLimiter.tryAcquireOtp(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ipRateLimiter.getRemainingSeconds("otp", httpReq)))
                    .body(ApiResponse.<Void>builder().success(false).message("Too many OTP requests. Please try again later.").build());
        }
        return ResponseEntity.ok(authService.requestPhoneOtp(request));
    }

    @PostMapping("/phone/verify-otp")
    @Operation(summary = "Verify phone OTP", description = "Verifies phone number with OTP")
    public ResponseEntity<ApiResponse<Void>> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyPhoneOtp(request));
    }

    // ─── Login: email/username + password ────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email/username + password")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ─── Login: Email + OTP ──────────────────────────────────────────

    @PostMapping("/login/email/request-otp")
    @Operation(summary = "Request email login OTP", description = "Sends OTP to email for passwordless login")
    public ResponseEntity<ApiResponse<Void>> requestEmailLoginOtp(@Valid @RequestBody EmailLoginOtpRequest request, HttpServletRequest httpReq) {
        if (!ipRateLimiter.tryAcquireOtp(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ipRateLimiter.getRemainingSeconds("otp", httpReq)))
                    .body(ApiResponse.<Void>builder().success(false).message("Too many OTP requests. Please try again later.").build());
        }
        return ResponseEntity.ok(authService.requestEmailLoginOtp(request));
    }

    @PostMapping("/login/email/verify-otp")
    @Operation(summary = "Login with email OTP")
    public ResponseEntity<AuthResponse> loginWithEmailOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.loginWithEmailOtp(request));
    }

    // ─── Login: Phone + OTP ──────────────────────────────────────────

    @PostMapping("/login/phone/request-otp")
    @Operation(summary = "Request phone login OTP", description = "Sends OTP via SMS for phone login")
    public ResponseEntity<ApiResponse<Void>> requestPhoneLoginOtp(@Valid @RequestBody PhoneOtpRequest request, HttpServletRequest httpReq) {
        if (!ipRateLimiter.tryAcquireOtp(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ipRateLimiter.getRemainingSeconds("otp", httpReq)))
                    .body(ApiResponse.<Void>builder().success(false).message("Too many OTP requests. Please try again later.").build());
        }
        return ResponseEntity.ok(authService.requestPhoneLoginOtp(request));
    }

    @PostMapping("/login/phone/verify-otp")
    @Operation(summary = "Login with phone OTP")
    public ResponseEntity<AuthResponse> loginWithPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        return ResponseEntity.ok(authService.loginWithPhoneOtp(request));
    }

    // ─── Session management ──────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refreshToken(body.get("refreshToken")));
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validate(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }

    // ─── Password recovery ───────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpReq) {
        if (!ipRateLimiter.tryAcquireForgotPassword(httpReq)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ipRateLimiter.getRemainingSeconds("forgotpw", httpReq)))
                    .body(ApiResponse.<Void>builder().success(false).message("Too many password reset requests. Please try again later.").build());
        }
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponse<String>> verifyResetOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyResetOtp(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    // ─── OAuth2 ──────────────────────────────────────────────────────

    @PostMapping("/oauth2/{provider}/callback")
    public ResponseEntity<AuthResponse> oAuth2Callback(@PathVariable String provider,
                                                        @Valid @RequestBody OAuth2CallbackRequest request) {
        return ResponseEntity.ok(authService.oAuth2Callback(provider, request));
    }

    // ─── User management ─────────────────────────────────────────────

    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserProfileDto> getProfile(
            @PathVariable int userId,
            @RequestHeader(value = "X-User-Id", required = false) String requesterId) {
        // P1-7: Rate-limit profile lookups to prevent user enumeration
        if (requesterId != null && !requesterId.isBlank()) {
            long epochMinute = Instant.now().getEpochSecond() / 60;
            String key = "ratelimit:profile:" + requesterId + ":" + epochMinute;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, 2, TimeUnit.MINUTES);
            }
            if (count != null && count > 60) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
        }
        return ResponseEntity.ok(authService.getPublicProfile(userId));
    }

    @PutMapping("/profile/{userId}")
    public ResponseEntity<UserProfileDto> updateProfile(@PathVariable int userId, @RequestHeader("X-User-Id") int tokenId, @RequestHeader("X-User-Role") String role, @Valid @RequestBody UpdateProfileRequest request) {
        if (userId != tokenId && !(role.equals("ADMIN") || role.equals("PLATFORM_ADMIN"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }

    @PutMapping("/password/{userId}")
    public ResponseEntity<Void> changePassword(@PathVariable int userId, @RequestHeader("X-User-Id") int tokenId, @RequestHeader("X-User-Role") String role, @Valid @RequestBody ChangePasswordRequest request) {
        if (userId != tokenId && !(role.equals("ADMIN") || role.equals("PLATFORM_ADMIN"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserProfileDto>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(authService.searchUsers(q));
    }

    @PutMapping("/status/{userId}")
    public ResponseEntity<Void> updateStatus(@PathVariable int userId, @RequestHeader("X-User-Id") int tokenId, @RequestHeader("X-User-Role") String role, @RequestBody Map<String, String> body) {
        if (userId != tokenId && !(role.equals("ADMIN") || role.equals("PLATFORM_ADMIN"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        authService.updateStatus(userId, body.get("status"));
        return ResponseEntity.noContent().build();
    }

    // GET /users moved to AdminResource for security

    @PostMapping("/users/batch")
    public ResponseEntity<List<UserProfileDto>> getUsersByIds(@RequestBody List<Integer> ids) {
        return ResponseEntity.ok(authService.getUsersByIds(ids));
    }

    // ─── P2-15: Self-Service Account Deletion ────────────────────────

    @DeleteMapping("/me")
    @Operation(summary = "Delete own account", description = "Self-service account deletion. Requires password for LOCAL accounts.")
    public ResponseEntity<ApiResponse<Void>> deleteOwnAccount(
            @RequestHeader("X-User-Id") int userId,
            @RequestBody(required = false) Map<String, String> body) {
        String password = body != null ? body.get("password") : null;
        authService.selfDeleteAccount(userId, password);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Account deleted successfully").build());
    }
}
