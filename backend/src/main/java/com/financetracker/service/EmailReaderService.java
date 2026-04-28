package com.financetracker.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.dto.ParsedSmsResultDTO;
import com.financetracker.model.*;
import com.financetracker.repository.*;
import com.financetracker.util.EncryptionUtil;
import com.financetracker.util.TransactionParsingUtil;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EmailReaderService {
    
    private static final String APPLICATION_NAME = "Finance Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    
    @Autowired
    private SmsParserService smsParserService;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private CategoryService categoryService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private FailedTransactionRepository failedTransactionRepository;
    
    @Autowired
    private UserEmailConfigRepository emailConfigRepository;
    
    @Autowired
    private EncryptionUtil encryptionUtil;
    
    @Autowired
    private GoogleOAuthService googleOAuthService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private Gmail gmailService;
    
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = EmailReaderService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(Paths.get(TOKENS_DIRECTORY_PATH).toFile()))
                .setAccessType("offline")
                .build();
        
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    private Gmail getGmailService() throws Exception {
        if (gmailService == null) {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            gmailService = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return gmailService;
    }
    
    private Gmail getGmailServiceForUser(UserEmailConfig config) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("UserEmailConfig cannot be null");
        }
        
        if (config.getUser() == null) {
            throw new IllegalArgumentException("User in config cannot be null");
        }
        
        try {
            if (config.getTokenExpiry() != null && config.getTokenExpiry().isBefore(Instant.now())) {
                log.info("Access token expired, refreshing for user: {}", config.getUser().getId());
                String newAccessToken = googleOAuthService.refreshAccessToken(config);
            }
            
            if (config.getEncryptedAccessToken() == null || config.getEncryptedAccessToken().isEmpty()) {
                throw new IllegalStateException("Encrypted access token is missing for user: " + config.getUser().getId());
            }
            
            String accessToken = encryptionUtil.decrypt(config.getEncryptedAccessToken());
            
            if (accessToken == null || accessToken.isEmpty()) {
                throw new IllegalStateException("Failed to decrypt access token for user: " + config.getUser().getId());
            }
            
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            
            return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to create Gmail service for user {}: {}",
                config.getUser().getId(), e.getMessage());
            throw e;
        }
    }
    
    private GoogleClientSecrets getClientSecrets() throws IOException {
        InputStream in = EmailReaderService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
    }
    
    @Scheduled(fixedRate = 3600000)
    public void readTransactionEmailsForAllUsers() {
        log.info("Starting multi-user Gmail transaction check...");
        
        List<UserEmailConfig> activeConfigs = emailConfigRepository.findByIsActiveTrue();
        
        if (activeConfigs.isEmpty()) {
            log.info("No active email configurations found");
            return;
        }
        
        log.info("Found {} active email configurations", activeConfigs.size());
        
        for (UserEmailConfig config : activeConfigs) {
            try {
                processUserEmails(config);
            } catch (Exception e) {
                log.error("Failed to process emails for user: {} ({}): {}",
                    config.getUser().getId(), config.getEmailAddress(), e.getMessage());
                log.debug("Full error details for user {}:", config.getUser().getId(), e);
            }
        }
        
        log.info("Multi-user Gmail processing complete");
    }
    
    private void processUserEmails(UserEmailConfig config) throws Exception {
        User user = config.getUser();
        log.info("Processing emails for user: {} ({})", user.getId(), config.getEmailAddress());
        
        Gmail service = getGmailServiceForUser(config);
        
        long tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS).getEpochSecond();
        
        String query = String.format(
            "after:%d (" +
            "subject:\"transaction alert\" OR " +
            "subject:\"debit alert\" OR " +
            "subject:\"credit alert\" OR " +
            "subject:\"account debited\" OR " +
            "subject:\"account credited\" OR " +
            "subject:\"payment alert\" OR " +
            "subject:\"transaction notification\" OR " +
            "subject:\"card transaction\" OR " +
            "subject:\"UPI transaction\" OR " +
            "subject:\"NEFT\" OR " +
            "subject:\"IMPS\" OR " +
            "subject:\"RTGS\"" +
            ")",
            tenDaysAgo
        );
        
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setQ(query)
                .setMaxResults(100L)
                .execute();
        
        List<Message> messages = response.getMessages();
        
        if (messages == null || messages.isEmpty()) {
            log.info("No transaction emails found for user: {}", user.getId());
            config.setLastSync(Instant.now());
            emailConfigRepository.save(config);
            return;
        }
        
        log.info("Found {} transaction emails for user: {}", messages.size(), user.getId());
        
        int successCount = 0;
        int failureCount = 0;
        int duplicateCount = 0;
        
        for (Message message : messages) {
            try {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();
                
                String subject = getEmailSubject(fullMessage);
                String emailContent = getEmailBody(fullMessage);
                String parseInput = buildEmailParseInput(subject, emailContent);
                
                log.debug("Processing email - Subject: {}", subject);
                
                ParsedSmsResultDTO parsedResult = smsParserService.parseSms(parseInput);
                parsedResult.setSource(TransactionSource.EMAIL);
                
                log.debug("Parsed result - AccountHint: {}, Amount: {}, Type: {}",
                    parsedResult.getAccountHint(), parsedResult.getAmount(), parsedResult.getTransactionType());
                
                log.info("Successfully parsed email transaction: Amount={}, Type={}, Description={}",
                    parsedResult.getAmount(), parsedResult.getTransactionType(), parsedResult.getDescription());
                
                Transaction transaction = convertToTransaction(parsedResult, user, emailContent, config.getId());
                
                if (transaction != null) {
                    if (isDuplicateTransactionDetailed(transaction, user.getId(), emailContent)) {
                        log.info("Skipping duplicate transaction - User: {}, Amount: {}, Type: {}, Date: {}, Email ID: {}",
                            user.getId(), transaction.getAmount(), transaction.getTransactionType(),
                            transaction.getTransactionDate(), message.getId());
                        duplicateCount++;
                        continue;
                    }
                    
                    boolean saved = saveTransaction(transaction, parsedResult, user, emailContent, config.getId());
                    if (saved) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } else {
                    log.warn("Account resolution failed for user {} - AccountHint: {}",
                        user.getId(), parsedResult.getAccountHint());
                    saveFailedTransaction(parsedResult, user, emailContent, config.getId(),
                        "Account resolution failed - No matching account found for hint: " + parsedResult.getAccountHint());
                    failureCount++;
                }
                
            } catch (Exception e) {
                log.error("Failed to process email ID {} for user {}: {}",
                    message.getId(), user.getId(), e.getMessage());
                log.debug("Full error details:", e);
                failureCount++;
            }
        }
        
        config.setLastSync(Instant.now());
        emailConfigRepository.save(config);
        
        log.info("User {} ({}) - Success: {}, Duplicates: {}, Failures: {}", 
            user.getId(), config.getEmailAddress(), successCount, duplicateCount, failureCount);
    }
    
    private Transaction convertToTransaction(ParsedSmsResultDTO parsed, User user,
                                            String rawEmail, Long emailConfigId) {
        if (parsed == null) {
            log.error("ParsedSmsResultDTO is null");
            return null;
        }
        
        if (user == null) {
            log.error("User is null");
            return null;
        }
        
        try {
            if (parsed.getAmount() == null) {
                log.warn("Amount is null for user {}", user.getId());
                return null;
            }
            
            if (parsed.getTransactionType() == null) {
                log.warn("Transaction type is null for user {}", user.getId());
                return null;
            }
            
            Account account = resolveAccount(parsed.getAccountHint(), user);
            if (account == null) {
                log.warn("Could not resolve account for user {} with hint: {}", user.getId(), parsed.getAccountHint());
                return null;
            }
            
            log.debug("Resolved account: ID={}, Type={}, Bank={}",
                account.getId(), account.getAccountType(), account.getBankName());
            
            Category category = categoryService.categorizeTransaction(parsed.getDescription());
            log.debug("Categorized as: {}", category != null ? category.getName() : "null");
            
            String safeDescription = TransactionParsingUtil.truncateString(
                parsed.getDescription() != null ? parsed.getDescription() : "Email transaction", 255);
            String safeRawText = TransactionParsingUtil.truncateString(rawEmail, 255);
            
            Transaction transaction = Transaction.builder()
                .account(account)
                .category(category)
                .amount(parsed.getAmount())
                .transactionType(parsed.getTransactionType())
                .source(TransactionSource.EMAIL)
                .description(safeDescription)
                .rawText(safeRawText)
                .transactionDate(parsed.getTransactionDate() != null ? parsed.getTransactionDate() : Instant.now())
                .build();
            
            log.debug("Created transaction object: Amount={}, Type={}, Date={}",
                transaction.getAmount(), transaction.getTransactionType(), transaction.getTransactionDate());
            
            return transaction;
        } catch (Exception e) {
            log.error("Error converting parsed result to transaction for user {}: {}",
                user.getId(), e.getMessage());
            log.debug("Conversion error details:", e);
            return null;
        }
    }
    
    private Account resolveAccount(String accountHint, User user) {
        if (!TransactionParsingUtil.isValidString(accountHint)) {
            log.warn("Empty or null account hint for user: {}", user != null ? user.getId() : "null");
            return null;
        }
        
        if (user == null) {
            log.error("User is null in resolveAccount");
            return null;
        }
        
        log.debug("Attempting to resolve account for user {} with hint: {}", user.getId(), accountHint);
        
        try {
            String last4Digits = TransactionParsingUtil.extractLast4Digits(accountHint);
            if (last4Digits != null) {
                log.debug("Extracted last 4 digits: {}", last4Digits);
                Optional<Account> account = accountRepository
                    .findByUserIdAndAccountNumberEndingWith(user.getId(), last4Digits);
                if (account.isPresent()) {
                    log.info("Account resolved by number for user {}: last4={}, accountId={}",
                        user.getId(), last4Digits, account.get().getId());
                    return account.get();
                } else {
                    log.debug("No account found with last 4 digits: {}", last4Digits);
                }
            }
            
            String bankName = TransactionParsingUtil.extractBankName(accountHint);
            AccountType accountType = TransactionParsingUtil.extractAccountType(accountHint);
            
            log.debug("Extracted bank: {}, type: {}", bankName, accountType);
            
            if (bankName != null && accountType != null) {
                Optional<Account> account = accountRepository
                    .findByUserIdAndBankNameAndAccountType(user.getId(), bankName, accountType);
                if (account.isPresent()) {
                    log.info("Account resolved by bank + type for user {}: bank={}, type={}, accountId={}",
                        user.getId(), bankName, accountType, account.get().getId());
                    return account.get();
                } else {
                    log.debug("No account found for bank {} and type {}", bankName, accountType);
                }
            }
            
            if (accountType != null) {
                List<Account> accounts = accountRepository
                    .findByUserIdAndAccountType(user.getId(), accountType);
                log.debug("Found {} accounts of type {} for user {}", accounts.size(), accountType, user.getId());
                if (accounts.size() == 1) {
                    log.info("Account resolved by type only for user {}: type={}, accountId={}",
                        user.getId(), accountType, accounts.get(0).getId());
                    return accounts.get(0);
                } else if (accounts.size() > 1) {
                    log.warn("Multiple accounts found for type {} - cannot auto-resolve", accountType);
                }
            }
            
            log.warn("Could not resolve account for user {} with hint: {} (last4={}, bank={}, type={})",
                user.getId(), accountHint, last4Digits, bankName, accountType);
            return null;
            
        } catch (Exception e) {
            log.error("Error resolving account for user {} with hint '{}': {}",
                user.getId(), accountHint, e.getMessage());
            return null;
        }
    }
    
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean isDuplicateTransactionDetailed(Transaction transaction, UUID userId, String rawEmail) {
        try {
            if (rawEmail != null && !rawEmail.trim().isEmpty()) {
                boolean duplicateByRawText = transactionRepository.existsByAccountUserIdAndRawText(userId, rawEmail);
                if (duplicateByRawText) {
                    log.debug("Duplicate found by rawText for user: {}", userId);
                    return true;
                }
            }
            
            if (transaction.getAccount() != null && transaction.getAmount() != null &&
                transaction.getTransactionType() != null && transaction.getTransactionDate() != null) {
                boolean exactMatch = transactionRepository.existsByAccountIdAndAmountAndTypeAndDate(
                    transaction.getAccount().getId(),
                    transaction.getAmount(),
                    transaction.getTransactionType(),
                    transaction.getTransactionDate());
                if (exactMatch) {
                    log.debug("Duplicate found by exact match for account: {}", transaction.getAccount().getId());
                    return true;
                }
                
                Instant startDate = transaction.getTransactionDate().minusSeconds(300);
                Instant endDate = transaction.getTransactionDate().plusSeconds(300);
                boolean similarTransaction = transactionRepository.existsSimilarTransaction(
                    transaction.getAccount().getId(),
                    transaction.getAmount(),
                    transaction.getTransactionType(),
                    startDate,
                    endDate);
                if (similarTransaction) {
                    log.debug("Similar transaction found within 5 minutes for account: {}", transaction.getAccount().getId());
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking for duplicate transaction: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveFailedTransaction(ParsedSmsResultDTO parsed, User user,
                                      String rawEmail, Long emailConfigId, String failureReason) {
        if (parsed == null || user == null) {
            log.error("Cannot save failed transaction: parsed or user is null");
            return;
        }
        
        try {
            String truncatedFailureReason = TransactionParsingUtil.truncateString(
                failureReason != null ? failureReason : "Unknown failure", 255);
            String truncatedDescription = TransactionParsingUtil.truncateString(parsed.getDescription(), 255);
            String truncatedAccountHint = TransactionParsingUtil.truncateString(parsed.getAccountHint(), 255);
            String truncatedEmailAccount = TransactionParsingUtil.truncateString(user.getEmail(), 255);
            
            FailedTransaction failed = FailedTransaction.builder()
                .user(user)
                .emailConfigId(emailConfigId)
                .emailAccount(truncatedEmailAccount)
                .rawEmailContent(rawEmail)
                .failureReason(truncatedFailureReason)
                .accountHint(truncatedAccountHint)
                .amount(parsed.getAmount())
                .transactionType(parsed.getTransactionType())
                .transactionDate(parsed.getTransactionDate() != null ? parsed.getTransactionDate() : Instant.now())
                .description(truncatedDescription)
                .parsedDataJson(convertToJson(parsed))
                .requiresManualReview(true)
                .resolved(false)
                .build();
            
            FailedTransaction saved = failedTransactionRepository.save(failed);
            log.info("Saved failed transaction ID {} for user {} - Reason: {}",
                saved.getId(), user.getId(), truncatedFailureReason);
        } catch (Exception e) {
            log.error("Failed to save failed transaction record for user {}: {}",
                user.getId(), e.getMessage());
            log.debug("Error saving failed transaction:", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private boolean saveTransaction(Transaction transaction, ParsedSmsResultDTO parsedResult,
                                    User user, String emailContent, Long emailConfigId) {
        if (transaction == null) {
            log.error("Cannot save null transaction");
            return false;
        }
        
        if (user == null) {
            log.error("Cannot save transaction: user is null");
            return false;
        }
        
        try {
            if (transaction.getDescription() != null && transaction.getDescription().length() > 255) {
                transaction.setDescription(TransactionParsingUtil.truncateString(transaction.getDescription(), 255));
            }
            if (transaction.getRawText() != null && transaction.getRawText().length() > 255) {
                transaction.setRawText(TransactionParsingUtil.truncateString(transaction.getRawText(), 255));
            }
            
            Transaction savedTransaction = transactionRepository.save(transaction);
            log.info("Transaction saved successfully - ID: {}, Amount: {}, User: {}",
                savedTransaction.getId(), savedTransaction.getAmount(), user.getId());
            return true;
        } catch (Exception saveEx) {
            log.error("Failed to save transaction to database for user {}: {}",
                user.getId(), saveEx.getMessage());
            log.debug("Save error details:", saveEx);
            
            if (parsedResult != null) {
                saveFailedTransaction(parsedResult, user, emailContent, emailConfigId,
                    "Database save failed: " + saveEx.getMessage());
            }
            return false;
        }
    }
    
    
    private String convertToJson(ParsedSmsResultDTO parsed) {
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            log.error("Failed to convert to JSON", e);
            return "{}";
        }
    }
    
    private String getEmailSubject(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            return message.getPayload().getHeaders().stream()
                    .filter(header -> "Subject".equalsIgnoreCase(header.getName()))
                    .map(header -> header.getValue())
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private String buildEmailParseInput(String subject, String emailContent) {
        try {
            String safeSubject = subject != null ? subject.trim() : "";
            String safeBody = emailContent != null ? emailContent.trim() : "";
            
            if (safeSubject.isEmpty() && safeBody.isEmpty()) {
                log.warn("Both subject and body are empty");
                return "";
            }
            
            if (safeSubject.isEmpty()) {
                return safeBody;
            }
            if (safeBody.isEmpty()) {
                return safeSubject;
            }
            return "Subject: " + safeSubject + "\n" + safeBody;
        } catch (Exception e) {
            log.error("Error building email parse input: {}", e.getMessage());
            return emailContent != null ? emailContent : "";
        }
    }
    
    private String getEmailBody(Message message) {
        if (message == null) {
            log.warn("Message is null in getEmailBody");
            return "";
        }
        
        StringBuilder body = new StringBuilder();
        
        try {
            if (message.getPayload() != null) {
                MessagePart payload = message.getPayload();
                
                if (payload.getBody() != null && payload.getBody().getData() != null) {
                    try {
                        String data = payload.getBody().getData();
                        body.append(new String(Base64.getUrlDecoder().decode(data)));
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to decode email body data: {}", e.getMessage());
                    }
                }
                
                if (payload.getParts() != null) {
                    for (MessagePart part : payload.getParts()) {
                        try {
                            if (part.getMimeType() != null &&
                                (part.getMimeType().equals("text/plain") || part.getMimeType().equals("text/html"))) {
                                if (part.getBody() != null && part.getBody().getData() != null) {
                                    String data = part.getBody().getData();
                                    body.append(new String(Base64.getUrlDecoder().decode(data)));
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to process email part: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting email body: {}", e.getMessage());
        }
        
        return body.toString();
    }
}