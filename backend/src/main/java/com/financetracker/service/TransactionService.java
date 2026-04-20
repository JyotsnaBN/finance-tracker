package com.financetracker.service;

import com.financetracker.dto.TransactionDTO;
import com.financetracker.model.Account;
import com.financetracker.model.Category;
import com.financetracker.model.Transaction;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.CategoryRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.util.EntityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final EntityMapper entityMapper;
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getAllTransactions() {
        log.debug("Fetching all transactions");
        List<Transaction> transactions = transactionRepository.findAll();
        return entityMapper.toTransactionDTOList(transactions);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactionsByUserId(UUID userId) {
        log.debug("Fetching transactions for user: {}", userId);
        List<Transaction> transactions = transactionRepository.findByAccountUserId(userId);
        return entityMapper.toTransactionDTOList(transactions);
    }
    
    @Transactional(readOnly = true)
    public TransactionDTO getTransactionById(Long id) {
        log.debug("Fetching transaction with id: {}", id);
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
        return entityMapper.toTransactionDTO(transaction);
    }
    
    @Transactional
    public TransactionDTO createTransaction(TransactionDTO dto) {
        log.info("Creating transaction: {}", dto);
        
        Account account = accountRepository.findById(dto.getAccountId())
            .orElseThrow(() -> new RuntimeException("Account not found with id: " + dto.getAccountId()));
        
        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
        
        Transaction transaction = Transaction.builder()
            .account(account)
            .category(category)
            .amount(dto.getAmount())
            .transactionType(dto.getTransactionType())
            .source(dto.getSource())
            .description(dto.getDescription())
            .rawText(dto.getRawText())
            .transactionDate(dto.getTransactionDate())
            .build();
        
        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction created with id: {}", saved.getId());
        
        return entityMapper.toTransactionDTO(saved);
    }
    
    @Transactional
    public TransactionDTO updateTransaction(Long id, TransactionDTO dto) {
        log.info("Updating transaction with id: {}", id);
        
        Transaction existing = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
        
        if (dto.getCategoryId() != null && 
            !dto.getCategoryId().equals(existing.getCategory().getId())) {
            Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
            existing.setCategory(category);
        }
        
        existing.setAmount(dto.getAmount());
        existing.setTransactionType(dto.getTransactionType());
        existing.setDescription(dto.getDescription());
        existing.setTransactionDate(dto.getTransactionDate());
        
        Transaction updated = transactionRepository.save(existing);
        log.info("Transaction updated: {}", id);
        
        return entityMapper.toTransactionDTO(updated);
    }
    
    @Transactional
    public void deleteTransaction(Long id) {
        log.info("Deleting transaction with id: {}", id);
        
        if (!transactionRepository.existsById(id)) {
            throw new RuntimeException("Transaction not found with id: " + id);
        }
        
        transactionRepository.deleteById(id);
        log.info("Transaction deleted: {}", id);
    }
}
