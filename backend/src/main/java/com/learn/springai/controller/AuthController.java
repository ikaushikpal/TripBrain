package com.learn.springai.controller;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.learn.springai.dto.auth.AuthRequestDTO;
import com.learn.springai.dto.auth.LoginResponseDTO;
import com.learn.springai.dto.auth.TokenRefreshRequestDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.model.RefreshToken;
import com.learn.springai.model.User;
import com.learn.springai.repository.RefreshTokenRepository;
import com.learn.springai.service.JwtService;
import com.learn.springai.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody AuthRequestDTO authRequestDTO) {
        User user = userService.loginEntity(authRequestDTO);
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = jwtService.createRefreshToken(user);

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(UserDTO.createFromUser(user))
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody TokenRefreshRequestDTO request) {
        String requestRefreshToken = request.getRefreshToken();
        if (requestRefreshToken == null || requestRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(jwtService::verifyExpiration)
                .map(token -> {
                    User user = token.getUser();
                    String accessToken = jwtService.generateAccessToken(user);
                    // Extend expiry of existing refresh token to support idempotent concurrent refreshes
                    RefreshToken updatedToken = jwtService.extendRefreshToken(token);

                    return ResponseEntity.ok(LoginResponseDTO.builder()
                            .accessToken(accessToken)
                            .refreshToken(updatedToken.getToken())
                            .user(UserDTO.createFromUser(user))
                            .build());
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token is not in database or has expired!"));
    }
}
