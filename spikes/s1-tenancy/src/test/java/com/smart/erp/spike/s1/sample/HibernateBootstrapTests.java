package com.smart.erp.spike.s1.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class HibernateBootstrapTests {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void theDialectComesFromConfigurationNotFromATenantConnection() {
        Dialect dialect = entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices()
                .getDialect();
        assertThat(dialect).isInstanceOf(PostgreSQLDialect.class);
        assertThat(dialect.getVersion().getDatabaseMajorVersion()).isEqualTo(18);
    }
}
