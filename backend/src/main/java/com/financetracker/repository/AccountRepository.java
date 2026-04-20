package com.financetracker.repository;

import com.financetracker.model.Account;
import com.financetracker.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);
    List<Account> findByUserIdAndIsActive(UUID userId, Boolean isActive);
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findByUserIdAndAccountType(UUID userId, AccountType accountType);
    
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumberAndUserId(String accountNumber, UUID userId);
    
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByIdAndUserId(UUID id, UUID userId);
    
    @Query("SELECT COUNT(a) FROM Account a WHERE a.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
    
    List<Account> findByUserIdAndIsActiveOrderByAccountNameAsc(UUID userId, Boolean isActive);
}
