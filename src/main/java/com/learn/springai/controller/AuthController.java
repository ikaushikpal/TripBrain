package com.learn.springai.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.learn.springai.dto.auth.AuthRequestDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.service.UserService;

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

    @PostMapping("/login")
    public ResponseEntity<UserDTO> postMethodName(@Valid @RequestBody AuthRequestDTO authRequestDTO) {
        UserDTO user = userService.login(authRequestDTO);
        return ResponseEntity.ok(user);
    }

}
