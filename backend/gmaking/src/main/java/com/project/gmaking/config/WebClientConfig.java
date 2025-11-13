package com.project.gmaking.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class WebClientConfig {

    @Value("${model.server.url}")
    private String modelServerUrl;

    @PostConstruct
    public void init() {
        log.info("### WebClientConfig 초기화 완료 — 빈 생성 시도됨");
    }

    @Bean("classificationWebClient")
    public WebClient classificationWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(60)));

        return WebClient.builder()
                .baseUrl(modelServerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}