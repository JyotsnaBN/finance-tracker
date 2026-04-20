package com.financetracker.service;

import com.financetracker.dto.AccountDTO;
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
            .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
        return entityMapper.toAccountDTO(account);
    }
    
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        log.info("Creating account: {}", dto.getAccountName());
        
        // Validate user exists
        User user = userRepository.findById(dto.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getUserId()));
        
        // Check if account number already exists
        if (dto.getAccountNumber() != null && 
            accountRepository.existsByAccountNumber(dto.getAccountNumber())) {
            throw new RuntimeException("Account already exists with number: " + dto.getAccountNumber());
        }
        
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
        log.info("Account created with id: {}", saved.getId());
        
        return entityMapper.toAccountDTO(saved);
    }
    
    @Transactional
    public AccountDTO updateAccount(UUID id, AccountDTO dto) {
        log.info("Updating account with id: {}", id);
        
        Account existing = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found with id: " + id));
        
        existing.setAccountName(dto.getAccountName());
        existing.setBankName(dto.getBankName());
        existing.setAccountType(dto.getAccountType());
        existing.setCurrentBalance(dto.getCurrentBalance());
        existing.setIsActive(dto.getIsActive());
        
        Account updated = accountRepository.save(existing);
        log.info("Account updated: {}", id);
        
        return entityMapper.toAccountDTO(updated);
    }
    
    @Transactional
    public void deleteAccount(UUID id) {
        log.info("Deleting account with id: {}", id);
        
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found with id: " + id);
        }
        
        accountRepository.deleteById(id);
        log.info("Account deleted: {}", id);
    }
}
