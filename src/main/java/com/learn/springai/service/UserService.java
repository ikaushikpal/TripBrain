package com.learn.springai.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.learn.springai.dto.auth.AuthRequestDTO;
import com.learn.springai.dto.user.NewUserDTO;
import com.learn.springai.dto.user.UserDTO;
import com.learn.springai.model.User;
import com.learn.springai.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

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

    public void deleteUser(String id) {
        User user = getUserEntityById(id);
        user.setDeleted(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public UserDTO login(AuthRequestDTO authRequestDTO) {
        User user = userRepository.findByEmail(authRequestDTO.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(authRequestDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        return UserDTO.createFromUser(user);
    }
}