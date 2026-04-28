package com.financetracker.service;

import com.financetracker.dto.AccountDTO;
import com.financetracker.exception.DuplicateResourceException;
import com.financetracker.model.Account;
import com.financetracker.model.User;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.EntityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    
    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        log.debug("Fetching all accounts");
        List<Account> accounts = accountRepository.findAll();
        return entityMapper.toAccountDTOList(accounts);
    }
    
    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountsByUserId(UUID userId) {
        log.debug("Fetching accounts for user: {}", userId);
        List<Account> accounts = accountRepository.findByUserId(userId);
        return entityMapper.toAccountDTOList(accounts);
    }
    
    @Transactional(readOnly = true)
    public AccountDTO getAccountById(UUID id) {
        log.debug("Fetching account with id: {}", id);
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        return entityMapper.toAccountDTO(account);
    }
    
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        log.info("Creating account: {} for user: {}", dto.getAccountName(), dto.getUserId());
        
        User user = userRepository.findById(dto.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (dto.getAccountNumber() != null &&
            accountRepository.existsByAccountNumber(dto.getAccountNumber())) {
            throw new DuplicateResourceException("An account with this number already exists");
        }
        
        try {
            Account account = Account.builder()
                .user(user)
                .accountName(dto.getAccountName())
                .accountNumber(dto.getAccountNumber())
                .bankName(dto.getBankName())
                .accountType(dto.getAccountType())
                .currentBalance(dto.getCurrentBalance() != null ? dto.getCurrentBalance() : BigDecimal.ZERO)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
            
            Account saved = accountRepository.save(account);
            log.info("Account created successfully with id: {}", saved.getId());
            
            return entityMapper.toAccountDTO(saved);
        } catch (Exception e) {
            log.error("Failed to create account for user {}: {}", dto.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create account", e);
        }
    }
    
    @Transactional
    public AccountDTO updateAccount(UUID id, AccountDTO dto) {
        log.info("Updating account with id: {}", id);
        
        Account existing = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        try {
            existing.setAccountName(dto.getAccountName());
            existing.setBankName(dto.getBankName());
            existing.setAccountType(dto.getAccountType());
            existing.setCurrentBalance(dto.getCurrentBalance());
            existing.setIsActive(dto.getIsActive());
            
            Account updated = accountRepository.save(existing);
            log.info("Account updated successfully: {}", id);
            
            return entityMapper.toAccountDTO(updated);
        } catch (Exception e) {
            log.error("Failed to update account {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update account", e);
        }
    }
    
    @Transactional
    public void deleteAccount(UUID id) {
        log.info("Deleting account with id: {}", id);
        
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found");
        }
        
        try {
            accountRepository.deleteById(id);
            log.info("Account deleted successfully: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete account {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete account. It may be referenced by existing transactions.", e);
        }
    }
}
