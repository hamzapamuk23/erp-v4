package com.smart.erp.spike.s1.tenancy;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GHOST;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class JdbcTenantDirectoryTests {

    @Autowired
    TenantDirectory directory;

    @Test
    void readsTheRegistryFromThePlatformDatabase() {
        assertThat(directory.find(ACME)).contains(new TenantDescriptor(ACME, "erp_t_acme", TenantStatus.ACTIVE));
        assertThat(directory.find(INITECH))
                .contains(new TenantDescriptor(INITECH, "erp_t_initech", TenantStatus.SUSPENDED));
        assertThat(directory.find(new TenantKey("nobody"))).isEmpty();
    }

    @Test
    void listsActiveTenantsInKeyOrder() {
        assertThat(directory.activeTenants()).containsExactly(ACME, GHOST, GLOBEX);
    }
}
