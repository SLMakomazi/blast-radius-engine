package com.madlanga.lab.payment.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
    @Bean
    RestClient downstreamClient(@Value("${lab.downstream-url}") String url,
                                @Value("${lab.connect-timeout}") Duration connectTimeout,
                                @Value("${lab.read-timeout}") Duration readTimeout) {
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
