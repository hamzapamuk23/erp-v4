package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.tenancy.PlatformDb;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class SessionConfiguration {

    /**
     * Spring Session would otherwise open its transactions with the application's only transaction manager, which sits
     * on the default DataSource: the tenant-routing one from Phase 1 on (design decision 9, ADR-0039). Spring Session
     * finds this bean by name. It is not a default candidate: a default TransactionOperations would make Boot back off
     * its own transactionTemplate, and application code would get this platform one.
     */
    @Bean(name = "springSessionTransactionOperations", defaultCandidate = false)
    TransactionOperations springSessionTransactionOperations(@PlatformDb DataSource platform) {
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(platform));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); // Spring Session's default
        return transactions;
    }
}
