package com.financetracker.controller;

import com.financetracker.dto.EmailConfigDTO;
import com.financetracker.dto.OAuthUrlResponseDTO;
import com.financetracker.model.User;
import com.financetracker.model.UserEmailConfig;
import com.financetracker.repository.UserEmailConfigRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.service.GoogleOAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/email")
@Slf4j
public class EmailConfigController {
    
    @Autowired
    private GoogleOAuthService oauthService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserEmailConfigRepository emailConfigRepository;
    
    @PostMapping("/connect")
    public ResponseEntity<OAuthUrlResponseDTO> initiateConnection(@RequestParam UUID userId) {
        log.info("Initiating email connection for user: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String authUrl = oauthService.generateAuthorizationUrl(userId);
        
        return ResponseEntity.ok(OAuthUrlResponseDTO.builder()
                .authorizationUrl(authUrl)
                .message("Please visit the URL to authorize Gmail access")
                .build());
    }
    
    @GetMapping("/oauth/callback")
    public ResponseEntity<String> handleOAuthCallback(
            @RequestParam String code,
            @RequestParam String state) {
        
        log.info("Handling OAuth callback for user state: {}", state);
        
        UUID userId = UUID.fromString(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserEmailConfig config = oauthService.exchangeCodeForTokens(code, user);
        
        log.info("Successfully connected email: {} for user: {}",
                config.getEmailAddress(), userId);
        
        return ResponseEntity.ok("Email account connected successfully! You can close this window.");
    }
    
    @GetMapping("/status")
    public ResponseEntity<List<EmailConfigDTO>> getEmailConfigs(@RequestParam UUID userId) {
        log.info("Fetching email configs for user: {}", userId);
        
        List<UserEmailConfig> configs = emailConfigRepository.findByUserId(userId);
        
        List<EmailConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnectEmail(
            @RequestParam UUID userId,
            @RequestParam String emailAddress) {
        
        log.info("Disconnecting email: {} for user: {}", emailAddress, userId);
        
        UserEmailConfig config = emailConfigRepository
                .findByUserIdAndEmailAddress(userId, emailAddress)
                .orElseThrow(() -> new RuntimeException("Email configuration not found"));
        
        config.setIsActive(false);
        emailConfigRepository.save(config);
        
        log.info("Email {} disconnected successfully for user: {}", emailAddress, userId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/sync")
    public ResponseEntity<String> triggerManualSync(
            @RequestParam UUID userId,
            @RequestParam String emailAddress) {
        
        log.info("Manual sync triggered for email: {} user: {}", emailAddress, userId);
        
        return ResponseEntity.ok("Sync initiated");
    }
    
    private EmailConfigDTO toDTO(UserEmailConfig config) {
        return EmailConfigDTO.builder()
                .id(config.getId())
                .emailAddress(config.getEmailAddress())
                .isActive(config.getIsActive())
                .lastSync(config.getLastSync())
                .tokenExpiry(config.getTokenExpiry())
                .tokenExpired(config.getTokenExpiry() != null && 
                             config.getTokenExpiry().isBefore(Instant.now()))
                .build();
    }
}
