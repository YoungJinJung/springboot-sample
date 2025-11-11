package com.example.demo.configuration.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RoutingDataSource extends AbstractRoutingDataSource {
    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {

        boolean isTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        String currentTransactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        log.info("isTransactionActive : {}, isReadOnly : {}, currentTransactionName : {}", isTransactionActive, isReadOnly, currentTransactionName);

        if (!isTransactionActive) {
            return DataSourceType.READER;
        }

        // 트랜잭션이 있는 경우 읽기 전용 여부에 따라 결정
        return isReadOnly
                ? DataSourceType.READER : DataSourceType.WRITER;
    }

    public enum DataSourceType {
        WRITER, READER
    }
}
