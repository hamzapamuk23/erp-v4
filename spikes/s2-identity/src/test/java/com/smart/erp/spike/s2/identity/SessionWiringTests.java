package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.smart.erp.spike.s2.support.SpikeTest;
import com.smart.erp.spike.s2.tenancy.TenantDataSourceStandIn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Design decision 9's second trap: Spring Session's own transaction hook must not become the application's. Boot
 * creates its transactionTemplate only while no default-candidate TransactionOperations exists; if the session one were
 * a default candidate, application code would lose Boot's template or silently get the platform DB's.
 */
@SpikeTest
class SessionWiringTests {

    @Autowired
    TransactionOperations transactions;

    @Autowired
    ApplicationContext context;

    @Test
    void applicationTransactionsStayOnTheDefaultDataSource() {
        assertThat(transactions).isSameAs(context.getBean("transactionTemplate"));
        assertThat(transactions)
                .asInstanceOf(type(TransactionTemplate.class))
                .extracting(TransactionTemplate::getTransactionManager)
                .isInstanceOfSatisfying(
                        DataSourceTransactionManager.class,
                        manager -> assertThat(manager.getDataSource()).isInstanceOf(TenantDataSourceStandIn.class));
    }
}
