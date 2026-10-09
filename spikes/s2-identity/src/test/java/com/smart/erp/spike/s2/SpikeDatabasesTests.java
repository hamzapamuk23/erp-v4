package com.smart.erp.spike.s2;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeDatabases;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpikeDatabasesTests {

    @Test
    void theRegistryAndTheSessionTablesExistInThePlatformDatabase() {
        List<String> tables = SpikeDatabases.platformDatabase()
                .sql("select tablename from pg_tables where schemaname = 'public' order by tablename")
                .query(String.class)
                .list();
        assertThat(tables).contains("tenant", "tenant_domain", "spring_session", "spring_session_attributes");
    }

    @Test
    void theApplicationRoleOwnsTheTables() {
        List<String> owners =
                SpikeDatabases.platformDatabase().sql("""
                        select distinct tableowner from pg_tables
                        where tablename in ('tenant', 'tenant_domain', 'spring_session', 'spring_session_attributes')
                        """).query(String.class).list();
        assertThat(owners).containsExactly("erp_platform");
    }

    @Test
    void thePlatformDatabaseIsClosedToPublic() {
        // The owner keeps CONNECT (so the ACL is not NULL, which would mean the default grants) and PUBLIC lost it.
        List<String> grantees =
                SpikeDatabases.platformDatabase().sql("""
                        select case a.grantee when 0 then 'PUBLIC' else a.grantee::regrole::text end
                        from pg_database d cross join lateral aclexplode(d.datacl) a
                        where d.datname = 'erp_platform' and a.privilege_type = 'CONNECT'
                        """).query(String.class).list();
        assertThat(grantees).contains("erp_platform").doesNotContain("PUBLIC");
    }
}
