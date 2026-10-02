package com.sonograma.service.importacion;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes the narrow logical-source receipt boundary through transaction completion. */
@Service
@RequiredArgsConstructor
public class ManualDiscogsReceiptLockService {

    private static final ReentrantLock[] SOURCE_LOCKS = new ReentrantLock[256];

    static {
        for (int index = 0; index < SOURCE_LOCKS.length; index++) {
            SOURCE_LOCKS[index] = new ReentrantLock(true);
        }
    }

    private final EntityManager entityManager;

    public void acquire(String normalizedSource) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El bloqueo de recepción requiere una transacción activa.");
        }
        ReentrantLock localLock = SOURCE_LOCKS[Math.floorMod(normalizedSource.hashCode(), SOURCE_LOCKS.length)];
        localLock.lock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                localLock.unlock();
            }
        });

        Object dialect = entityManager.getEntityManagerFactory().getProperties().get("hibernate.dialect");
        if (dialect != null && dialect.toString().toLowerCase().contains("postgres")) {
            long key = Integer.toUnsignedLong(normalizedSource.hashCode());
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(CAST(:lockKey AS BIGINT))")
                    .setParameter("lockKey", key)
                    .getSingleResult();
        }
    }
}
