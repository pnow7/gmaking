package com.project.gmaking.character.service;

import com.project.gmaking.character.exception.ClassificationFailedException;
import com.project.gmaking.character.vo.ClassificationResponseVO;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Service
public class ClassificationServiceImpl implements ClassificationService {

    private final WebClient webClient;
    private final String modelServerUrl;

    @Value("${model.server.classify.path}")
    private String classifyPath;

    @Value("${classification.threshold:0.80}")
    private double confidenceThreshold;

    public ClassificationServiceImpl(
            WebClient.Builder webClientBuilder,
            @Value("${model.server.url}") String modelServerUrl
    ) {
        this.modelServerUrl = modelServerUrl;

        log.info("모델 서버 URL 로드 완료: {}", modelServerUrl);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(60)));

        // baseUrl이 '/'로 끝나지 않으면 자동 추가
        String fixedBaseUrl = modelServerUrl.endsWith("/") ? modelServerUrl : modelServerUrl + "/";

        this.webClient = webClientBuilder
                .baseUrl(fixedBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<String> classifyImage(MultipartFile imageFile) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", imageFile.getResource());

        // baseUrl + path 조합 로그 출력
        String fullUrl = modelServerUrl + classifyPath;
        log.info("🛰️ 이미지 분류 요청 시작 → 엔드포인트: {}", classifyPath);
        log.info("🛰️ 최종 요청 URL = {}", fullUrl);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path(classifyPath).build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("🧩 모델 서버 응답 RAW: {}", body))
                .flatMap(raw -> {
                    log.info("🧠 테스트 단계 — 응답 수신 완료");
                    return Mono.just("ok");
                })
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    String msg = String.format("❌ 모델 서버 통신 오류: %s (엔드포인트: %s)", e.getMessage(), classifyPath);
                    log.error(msg);
                    return Mono.error(new RuntimeException("이미지 분류 서버에 연결할 수 없거나 응답이 잘못되었습니다. (설정값 확인 요망)"));
                });
    }

}
