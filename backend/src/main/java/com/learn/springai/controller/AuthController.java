package com.learn.springai.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.learn.springai.dto.auth.AuthRequestDTO;
import com.learn.springai.dto.auth.LoginResponseDTO;
import com.learn.springai.dto.auth.TokenRefreshRequestDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.model.User;
import com.learn.springai.model.RefreshToken;
import com.learn.springai.service.UserService;
import com.learn.springai.service.JwtService;
import com.learn.springai.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
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

        return refreshTokenRepository.findByToken(requestRefreshToken)
                .map(jwtService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtService.generateAccessToken(user);
                    RefreshToken newRefreshToken = jwtService.createRefreshToken(user);
                    return ResponseEntity.ok(LoginResponseDTO.builder()
                            .accessToken(accessToken)
                            .refreshToken(newRefreshToken.getToken())
                            .user(UserDTO.createFromUser(user))
                            .build());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }
}

