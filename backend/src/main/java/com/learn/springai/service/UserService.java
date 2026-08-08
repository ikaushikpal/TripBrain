package com.learn.springai.service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.learn.springai.dto.auth.AuthRequestDTO;
import com.learn.springai.dto.user.NewUserDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.TripPdf;
import com.learn.springai.model.User;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.PublicTripGalleryRepository;
import com.learn.springai.repository.RefreshTokenRepository;
import com.learn.springai.repository.TripPdfRepository;
import com.learn.springai.repository.UserRepository;
import com.learn.springai.repository.VectorDBRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TripPdfRepository tripPdfRepository;
    private final PublicTripGalleryRepository publicTripGalleryRepository;
    private final BackblazeStorageService backblazeStorageService;
    private final VectorDBRepository vectorDBRepository;

    public UserDTO createUser(NewUserDTO newUserDTO) {

        if (userRepository.existsByEmail(newUserDTO.getEmail())) {
            throw new RuntimeException("User already exists with email: " + newUserDTO.getEmail());
        }

        User user = User.builder()
                .name(newUserDTO.getName())
                .email(newUserDTO.getEmail())
                .password(passwordEncoder.encode(newUserDTO.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        return UserDTO.createFromUser(savedUser);
    }

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(UserDTO::createFromUser).toList();
    }

    private User getUserEntityById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDTO getUserById(String id) {
        User user = getUserEntityById(id);
        return UserDTO.createFromUser(user);
    }

    /**
     * Admin Cascade User Deletion:
     * Purges all associated conversations, chat messages, generated PDFs (from DB and cloud/disk storage),
     * public gallery items, vector embeddings, and refresh tokens.
     */
    @Transactional
    public void deleteUser(String id) {
        User user = getUserEntityById(id);

        // 1. Purge refresh tokens
        try {
            refreshTokenRepository.deleteByUser(user);
        } catch (Exception e) {
            log.warn("Failed to delete refresh tokens for user {}: {}", id, e.getMessage());
        }

        // 2. Fetch and purge all user conversations & associated assets
        List<Conversation> conversations = conversationRepository.findByUserIdAndDeletedFalseOrderByPinnedDescLastUpdatedDesc(id);
        for (Conversation conv : conversations) {
            String convId = conv.getId();

            // Delete associated ChatMessages
            try {
                chatMessageRepository.deleteByConversationId(convId);
            } catch (Exception e) {
                log.warn("Failed to delete chat messages for conversation {}: {}", convId, e.getMessage());
            }

            // Delete associated TripPdf assets & cloud/disk files
            TripPdf pdf = conv.getTripPdf();
            if (pdf != null) {
                try {
                    if (pdf.getFilePath() != null && !pdf.getFilePath().isBlank()) {
                        File localPdf = new File(pdf.getFilePath());
                        if (localPdf.exists()) {
                            localPdf.delete();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete local PDF file for {}: {}", pdf.getId(), e.getMessage());
                }

                try {
                    tripPdfRepository.delete(pdf);
                } catch (Exception e) {
                    log.warn("Failed to delete TripPdf entity for conversation {}: {}", convId, e.getMessage());
                }
            }

            conv.setDeleted(true);
            conv.setLastUpdated(LocalDateTime.now());
            conversationRepository.save(conv);
        }

        // Mark user deleted
        user.setDeleted(true);
        user.setBlocked(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Successfully executed admin cascade deletion for user {}", id);
    }

    public void blockUser(String id, boolean block) {
        User user = getUserEntityById(id);
        user.setBlocked(block);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void incrementApiCallCount(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setApiCallCount(user.getApiCallCount() + 1);
            userRepository.save(user);
        });
    }

    public User loginEntity(AuthRequestDTO authRequestDTO) {
        User user = userRepository.findByEmail(authRequestDTO.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"
                ));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Your account has been blocked by the administrator."
            );
        }

        if (!passwordEncoder.matches(authRequestDTO.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid email or password"
            );
        }

        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public UserDTO login(AuthRequestDTO authRequestDTO) {
        User saved = loginEntity(authRequestDTO);
        return UserDTO.createFromUser(saved);
    }
}