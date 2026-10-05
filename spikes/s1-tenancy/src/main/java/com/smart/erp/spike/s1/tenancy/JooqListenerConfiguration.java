package com.smart.erp.spike.s1.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Not named JooqConfiguration: Boot's jOOQ auto-configuration already defines a bean called jooqConfiguration. */
@Configuration(proxyBeanMethods = false)
class JooqListenerConfiguration {

    /** Boot adds every ExecuteListenerProvider bean to jOOQ's configuration. */
    @Bean
    DefaultExecuteListenerProvider flushBeforeJooqQuery(EntityManagerFactory entityManagerFactory) {
        return new DefaultExecuteListenerProvider(new FlushBeforeJooqQueryListener(entityManagerFactory));
    }
}
