package com.ohmdevs.hrsaas.platform.tenancy;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Conecta las capas de aislamiento sin que ningún módulo de negocio tenga que
 * saber que existen.
 */
@Configuration(proxyBeanMethods = false)
public class TenancyConfiguration {

    /**
     * Envuelve el {@link DataSource} que autoconfigura Spring Boot, en vez de
     * declarar uno propio: así se conservan el pool, las métricas y los health
     * checks tal como los configura Boot, y solo se intercepta el checkout.
     *
     * <p>Es {@code static} porque un {@link BeanPostProcessor} se instancia muy
     * temprano; sin ello Spring tendría que crear la clase de configuración
     * completa antes de tiempo y avisaría de ello en el arranque.
     */
    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }

    @Bean
    PlatformContextTaskDecorator platformContextTaskDecorator() {
        return new PlatformContextTaskDecorator();
    }
}
