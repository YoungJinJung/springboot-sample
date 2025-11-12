package com.example.demo.configuration.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;
import software.amazon.awssdk.services.rds.model.RdsException;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Profile("!local")
public class DatasourceConfiguration {
    private static RdsClient rdsClient = RdsClient.builder().region(Region.AP_NORTHEAST_2).build();
    private static final Logger log = LoggerFactory.getLogger(DatasourceConfiguration.class);

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

        String writerAuthToken = getAuthToken(rdsClient, writerUrl, writerUsername);
        writerDataSourceProperties.setPassword(writerAuthToken);
        log.info("Url: {}, UserName: {}, pw: {}",writerUrl,writerUsername,writerAuthToken);

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

        String readerAuthToken = getAuthToken(rdsClient, readerUrl, readerUsername);
        readerDataSourceProperties.setPassword(readerAuthToken);
        log.info("Url: {}, UserName: {}, pw: {}",readerUrl,readerUsername,readerAuthToken);

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

    private String[] extractHostAndPort(String jdbcUrl) {
        String pattern = "://([^/]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(jdbcUrl);

        if (m.find()) {
            String hostPort = m.group(1);
            String[] parts = hostPort.split(":");
            return new String[]{parts[0], parts[1]};
        }
        return null;
    }

    private String getAuthToken(RdsClient rdsClient, String url, String username) {

        RdsUtilities utilities = rdsClient.utilities();
        String[] endpoint = extractHostAndPort(url);
        log.info("host: {}, port: {}",endpoint[0],endpoint[1]);

        try {
            GenerateAuthenticationTokenRequest tokenRequest = GenerateAuthenticationTokenRequest.builder()
                    .credentialsProvider(ContainerCredentialsProvider.create())
                    .username(username)
                    .port(Integer.parseInt(endpoint[1]))
                    .hostname(endpoint[0])
                    .region(Region.AP_NORTHEAST_2)
                    .build();

            return utilities.generateAuthenticationToken(tokenRequest);

        } catch (RdsException e) {
            System.out.println(e.getLocalizedMessage());
        }
        return "";
    }
}
