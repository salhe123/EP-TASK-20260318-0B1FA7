package com.anju.appointment.auth.controller;

import com.anju.appointment.auth.dto.ChangePasswordRequest;
import com.anju.appointment.auth.dto.LoginRequest;
import com.anju.appointment.auth.dto.LoginResponse;
import com.anju.appointment.auth.security.AuthenticatedUser;
import com.anju.appointment.auth.security.JwtProvider;
import com.anju.appointment.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;
    private final boolean secureCookies;

    public AuthController(AuthService authService, JwtProvider jwtProvider,
                          @Value("${app.security.secure-cookies:true}") boolean secureCookies) {
        this.authService = authService;
        this.jwtProvider = jwtProvider;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);
        setAuthCookies(response, loginResponse.getAccessToken(), loginResponse.getRefreshToken());
        // Return response without refresh token in body — it is transported via HttpOnly cookie only
        LoginResponse safeResponse = LoginResponse.builder()
                .accessToken(loginResponse.getAccessToken())
                .forcePasswordReset(loginResponse.isForcePasswordReset())
                .role(loginResponse.getRole())
                .build();
        return ResponseEntity.ok(safeResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        LoginResponse loginResponse = authService.refresh(refreshToken);
        setAuthCookies(response, loginResponse.getAccessToken(), loginResponse.getRefreshToken());
        LoginResponse safeResponse = LoginResponse.builder()
                .accessToken(loginResponse.getAccessToken())
                .forcePasswordReset(loginResponse.isForcePasswordReset())
                .role(loginResponse.getRole())
                .build();
        return ResponseEntity.ok(safeResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal,
                                        HttpServletResponse response) {
        authService.logout(principal.getUserId());
        clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        String sameSite = secureCookies ? "Strict" : "Lax";
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/")
                .maxAge(jwtProvider.getAccessTokenExpirySeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        if (refreshToken != null) {
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(secureCookies)
                    .sameSite(sameSite)
                    .path("/api/auth")
                    .maxAge(jwtProvider.getRefreshTokenExpirySeconds())
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    private void clearAuthCookies(HttpServletResponse response) {
        String sameSite = secureCookies ? "Strict" : "Lax";
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
