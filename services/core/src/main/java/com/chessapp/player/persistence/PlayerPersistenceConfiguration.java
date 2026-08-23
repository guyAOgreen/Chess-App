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
 * <p>Enabling that validation turns a silent, wrong guarantee into a runtime
 * failure at the point a caller joins: the first time a caller's transaction
 * reaches {@code createOrFind} with a mismatched (or unspecified) isolation
 * level, Spring throws
 *
 * <pre>{@code
 * org.springframework.transaction.IllegalTransactionStateException: Participating
 * transaction with definition [PROPAGATION_REQUIRED,ISOLATION_READ_COMMITTED]
 * specifies isolation level which is incompatible with existing transaction:
 * (unknown)
 * }</pre>
 *
 * <p>Note that "(unknown)" covers a caller that never declared an isolation level
 * at all — a plain {@code @Transactional} is rejected exactly like one that
 * declares a different level. Any caller that opens its own transaction around
 * {@code createOrFind} must declare {@code isolation = Isolation.READ_COMMITTED}
 * to join successfully.
 */
@Configuration
class PlayerPersistenceConfiguration {

    @Bean
    TransactionManagerCustomizer<AbstractPlatformTransactionManager> validateExistingPlayerTransaction() {
        return manager -> manager.setValidateExistingTransaction(true);
    }
}
