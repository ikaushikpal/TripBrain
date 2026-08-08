package com.learn.springai.controller;

import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Administrative User & System Operations.
 * Secured with method-level authorization (@PreAuthorize("hasRole('ADMIN')")).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> listAllUsers() {
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
            @RequestParam boolean block) {
        userService.blockUser(userId, block);
        return ResponseEntity.ok("User block status successfully updated to: " + block);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok("User cascade deletion successfully executed.");
    }
}
