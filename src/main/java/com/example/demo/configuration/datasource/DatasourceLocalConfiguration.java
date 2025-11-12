package com.example.demo.configuration.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Profile("local")
public class DatasourceLocalConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DatasourceLocalConfiguration.class);

    @Bean(name = "writerDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.writer")
    public DataSourceProperties writerDataSourceProperties(){
        return new DataSourceProperties();
    }
    @Bean(name = "writerDataSource")
    public DataSource writerDataSource(@Qualifier("writerDataSourceProperties") DataSourceProperties writerDataSourceProperties) {
        String writerUsername = writerDataSourceProperties.getUsername();
        String writerUrl = writerDataSourceProperties.getUrl();
        log.info("Url: {}, UserName: {}",writerUrl,writerUsername);

        return writerDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "readerDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.reader")
    public DataSourceProperties readerDataSourceProperties(){
        return new DataSourceProperties();
    }
    @Bean(name = "readerDataSource")
    public DataSource readerDataSource(@Qualifier("readerDataSourceProperties") DataSourceProperties readerDataSourceProperties) {
        String readerUsername = readerDataSourceProperties.getUsername();
        String readerUrl = readerDataSourceProperties.getUrl();
        log.info("Url: {}, UserName: {}",readerUrl,readerUsername);

        return readerDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @DependsOn({"writerDataSource", "readerDataSource"})
    public DataSource routingDataSource(
            @Qualifier("writerDataSource") DataSource writerDataSource,
            @Qualifier("readerDataSource") DataSource readerDataSource) {

        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(RoutingDataSource.DataSourceType.WRITER, writerDataSource);
        targetDataSources.put(RoutingDataSource.DataSourceType.READER, readerDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writerDataSource);

        return routingDataSource;
    }

    @Bean("dataSource")
    @Primary
    @DependsOn({"routingDataSource"})
    public DataSource dataSource() {
        return new LazyConnectionDataSourceProxy(routingDataSource(writerDataSource(writerDataSourceProperties()), readerDataSource(readerDataSourceProperties())));
    }
}
