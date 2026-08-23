package com.chessapp.player.persistence;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/**
 * Makes a transaction-isolation mismatch fail loudly instead of silently.
 *
 * <p>{@link PlayerRepositoryAdapter#createOrFind} declares
 * {@code @Transactional(isolation = READ_COMMITTED)}, but under Spring's default
 * {@code REQUIRED} propagation that attribute is only honoured when this method
 * starts the transaction. A caller that already has one open — PGN import, for
 * example — makes {@code createOrFind} join it instead, and Spring silently
 * ignores the declared isolation level unless the transaction manager is told to
 * validate existing transactions.
 *
 * <p>Enabling that validation turns a silent, wrong guarantee into a startup-time
 * class of bug that instead throws {@code IllegalTransactionStateException} the
 * first time a caller actually joins with a mismatched isolation level, rather
 * than quietly running the upsert under whatever isolation the caller happened to
 * open.
 */
@Configuration
class PlayerPersistenceConfiguration {

    @Bean
    TransactionManagerCustomizer<AbstractPlatformTransactionManager> validateExistingPlayerTransaction() {
        return manager -> manager.setValidateExistingTransaction(true);
    }
}
