package com.example.CWMS.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration de la DataSource principale → CWMSDB
 *
 * FIX v2 :
 *   - cwmsTransactionManager prend EntityManagerFactory (pas LocalContainer...)
 *     pour éviter un getObject() null pendant l'initialisation du contexte.
 *   - ErpEmplacement.java doit être corrigé en parallèle (t_nama dupliqué).
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.example.CWMS.repository",
                "com.example.CWMS.transfer.repository"
        },
        entityManagerFactoryRef = "cwmsEntityManagerFactory",
        transactionManagerRef   = "cwmsTransactionManager"
)
public class CwmsDataSourceConfig {

    @Primary
    @Bean(name = "cwmsDataSourceProperties")
    @ConfigurationProperties("spring.datasource.cwms")
    public DataSourceProperties cwmsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "cwmsDataSource")
    public DataSource cwmsDataSource(
            @Qualifier("cwmsDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "cwmsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean cwmsEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("cwmsDataSource") DataSource dataSource) {

        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.hbm2ddl.auto", "update");
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        jpaProps.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        jpaProps.put("hibernate.show_sql", "false");

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.example.CWMS.model",
                        "com.example.CWMS.transfer.model"
                )
                .persistenceUnit("cwmsPU")
                .properties(jpaProps)
                .build();
    }

    /**
     * FIX : injecter EntityManagerFactory directement (pas LocalContainerEntityManagerFactoryBean)
     * Cela évite l'erreur "jpaMappingContext" lors du démarrage avec double datasource.
     */
    @Primary
    @Bean(name = "cwmsTransactionManager")
    public PlatformTransactionManager cwmsTransactionManager(
            @Qualifier("cwmsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}