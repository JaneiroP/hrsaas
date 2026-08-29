package com.ohmdevs.hrsaas.platform.tenancy;

import com.ohmdevs.hrsaas.platform.web.PlatformContextFilter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.sql.DataSource;

/**
 * Conecta las capas de aislamiento sin que ningún módulo de negocio tenga que
 * saber que existen.
 *
 * <p>{@code @EnableAsync} habilita el procesamiento de {@code TaskDecorator}
 * sobre los executors de la aplicación (incluido el que usará el
 * {@code PartitionHandler} de Spring Batch): sin esta anotación, Spring nunca
 * activa el soporte de {@code @Async} ni compone los decoradores registrados.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
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

    /**
     * Registra la primera capa de aislamiento (petición → contexto).
     *
     * <p>Orden {@code DEFAULT_FILTER_ORDER + 1}: justo después de la cadena de
     * Spring Security, para que {@code SecurityContextHolder} ya tenga la
     * {@link org.springframework.security.core.Authentication} resuelta
     * cuando este filtro la lea. Registrarlo antes lo dejaría siempre viendo
     * una petición sin autenticar.
     */
    @Bean
    FilterRegistrationBean<PlatformContextFilter> platformContextFilter() {
        FilterRegistrationBean<PlatformContextFilter> registration =
                new FilterRegistrationBean<>(new PlatformContextFilter());
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
