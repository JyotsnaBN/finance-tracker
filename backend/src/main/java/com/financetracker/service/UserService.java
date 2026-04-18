package com.financetracker.service;

import com.financetracker.dto.UserDTO;
import com.financetracker.model.User;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.EntityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        log.debug("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return entityMapper.toUserDTO(user);
    }
    
    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        log.debug("Fetching user with email: {}", email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return entityMapper.toUserDTO(user);
    }
    
    @Transactional
    public UserDTO createUser(UserDTO dto) {
        log.info("Creating user: {}", dto.getUsername());
        
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already exists: " + dto.getEmail());
        }
        
        User user = User.builder()
            .username(dto.getUsername())
            .email(dto.getEmail())
            .passwordHash(dto.getPasswordHash())
            .build();
        
        User saved = userRepository.save(user);
        log.info("User created with id: {}", saved.getId());
        
        return entityMapper.toUserDTO(saved);
    }
    
    @Transactional
    public UserDTO updateUser(Long id, UserDTO dto) {
        log.info("Updating user with id: {}", id);
        
        User existing = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        if (!existing.getEmail().equals(dto.getEmail()) && 
            userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already exists: " + dto.getEmail());
        }
        
        existing.setUsername(dto.getUsername());
        existing.setEmail(dto.getEmail());
        
        User updated = userRepository.save(existing);
        log.info("User updated: {}", id);
        
        return entityMapper.toUserDTO(updated);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user with id: {}", id);
        
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }
}
