package com.example.CWMS.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration de la DataSource secondaire → ERP (lecture seule)
 * Règle absolue : aucune écriture dans la base ERP.
 * ddl-auto = none → Hibernate ne touche jamais au schéma ERP.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {"com.example.CWMS.repository.erp"},
        entityManagerFactoryRef = "erpEntityManagerFactory",
        transactionManagerRef   = "erpTransactionManager"
)
public class ErpDataSourceConfig {

    @Bean(name = "erpDataSourceProperties")
    @ConfigurationProperties("spring.datasource.erp")
    public DataSourceProperties erpDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "erpDataSource")
    public DataSource erpDataSource(
            @Qualifier("erpDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "erpEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean erpEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("erpDataSource") DataSource dataSource) {

        Map<String, Object> jpaProps = new HashMap<>();
        // ⚠️ CRITIQUE : none = Hibernate ne modifie jamais le schéma ERP
        jpaProps.put("hibernate.hbm2ddl.auto", "none");
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        jpaProps.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        jpaProps.put("hibernate.show_sql", "false");

        return builder
                .dataSource(dataSource)
                .packages("com.example.CWMS.model.erp")
                .persistenceUnit("erpPU")
                .properties(jpaProps)
                .build();
    }

    @Bean(name = "erpTransactionManager")
    public PlatformTransactionManager erpTransactionManager(
            @Qualifier("erpEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(factory.getObject());
    }
    @Bean(name = "erpNamedJdbc")
    public NamedParameterJdbcTemplate erpNamedJdbc(
            @Qualifier("erpDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}