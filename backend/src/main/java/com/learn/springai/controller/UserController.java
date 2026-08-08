package com.learn.springai.controller;

import java.util.List;
import javax.validation.Valid;

import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.learn.springai.dto.user.NewUserDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Controller for User Accounts.
 * Configured with method-level authorization.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody NewUserDTO userDto) {
        UserDTO createdUser = userService.createUser(userDto);
        return ResponseEntity.status(HttpStatus.SC_CREATED).body(createdUser);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<UserDTO> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}