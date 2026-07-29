package com.learn.springai.controller;

import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    private void authorizeAdmin(String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing requester credential header 'X-Admin-Requester-Id'");
        }
        try {
            UserDTO requester = userService.getUserById(requesterId);
            if (!"ADMIN".equalsIgnoreCase(requester.getRole())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. Requester is not a system administrator.");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied. Invalid administrator credentials.");
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> listAllUsers(
            @RequestHeader(value = "X-Admin-Requester-Id", required = false) String requesterId) {
        authorizeAdmin(requesterId);
        List<UserDTO> users = userService.getAllUsers();
        // Clear conversations field on list view to protect user privacy
        for (UserDTO user : users) {
            user.setConversations(null);
        }
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<String> toggleUserBlock(
            @PathVariable String userId,
            @RequestParam boolean block,
            @RequestHeader(value = "X-Admin-Requester-Id", required = false) String requesterId) {
        authorizeAdmin(requesterId);
        userService.blockUser(userId, block);
        return ResponseEntity.ok("User block status successfully updated to: " + block);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(
            @PathVariable String userId,
            @RequestHeader(value = "X-Admin-Requester-Id", required = false) String requesterId) {
        authorizeAdmin(requesterId);
        userService.deleteUser(userId);
        return ResponseEntity.ok("User soft-deletion successfully executed.");
    }
}
