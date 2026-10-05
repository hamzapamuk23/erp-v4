package com.smart.erp.spike.s1.tenancy;

import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;

/**
 * Spring Modulith 2.1.1 has no multi-tenancy (doc §4.4) and touches its DataSource where no tenant is bound:
 *
 * <ul>
 *   <li>{@code JdbcEventPublicationAutoConfiguration#databaseType} reads JDBC metadata at start-up to pick SQL. The
 *       bean is unconditional and its type package-private, so it is answered here, without a connection.
 *   <li>{@code DefaultEventPublicationRegistry#destroy()} lists incomplete publications at shutdown; replaced by
 *       {@link TenantSafeEventPublicationRegistry}.
 * </ul>
 *
 * Both are matched by bean name and class, so a Modulith upgrade that renames them makes StartupIsolationTests fail
 * instead of silently bringing the access back.
 */
final class ModulithTenancySupport implements InstantiationAwareBeanPostProcessor, BeanFactoryAware {

    static final String DATABASE_TYPE_BEAN = "databaseType";
    static final String DATABASE_TYPE_CLASS = "org.springframework.modulith.events.jdbc.DatabaseType";
    static final String REGISTRY_BEAN = "eventPublicationRegistry";

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        if (DATABASE_TYPE_BEAN.equals(beanName) && DATABASE_TYPE_CLASS.equals(beanClass.getName())) {
            return postgres(beanClass);
        }
        if (REGISTRY_BEAN.equals(beanName) && DefaultEventPublicationRegistry.class.equals(beanClass)) {
            BeanFactory factory = Objects.requireNonNull(beanFactory, "beanFactory");
            return new TenantSafeEventPublicationRegistry(
                    factory.getBean(EventPublicationRepository.class),
                    factory.getBeanProvider(Clock.class).getIfAvailable(Clock::systemUTC));
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object postgres(Class<?> databaseType) {
        return Enum.valueOf((Class) databaseType, "POSTGRES");
    }
}
