package com.smart.erp.spike.s2.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s2.kernel.MissingTenantContextException;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TenantDataSourceStandInTests {

    private final TenantDataSourceStandIn standIn = new TenantDataSourceStandIn();

    @Test
    void aConnectionIsRefusedAndCounted() {
        assertThatThrownBy(standIn::getConnection).isInstanceOf(MissingTenantContextException.class);
        assertThat(standIn.requests()).isEqualTo(1);
    }

    @Test
    void wrapperIntrospectionIsAnsweredWithoutRoutingAndIsNotCounted() throws SQLException {
        assertThat(standIn.unwrap(DataSource.class)).isSameAs(standIn);
        assertThat(standIn.isWrapperFor(HikariDataSource.class)).isFalse();
        assertThat(standIn.requests()).isZero();
    }
}
