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

    @Value("${model.server.classify.path}")
    private String classifyPath;

    @Value("${classification.threshold:0.80}")
    private double confidenceThreshold;

    public ClassificationServiceImpl(
            WebClient.Builder webClientBuilder,
            @Value("${model.server.url}") String modelServerUrl
    ) {
        // 모델 서버 URL 확인 로그
        log.info("모델 서버 URL 로드 완료: {}", modelServerUrl);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(10)));

        // baseUrl이 '/'로 끝나지 않으면 자동 추가 (경로 조합 오류 방지)
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

        // 실제 요청할 URL 로그 출력
        log.info("이미지 분류 요청 시작 → 엔드포인트: {}", classifyPath);
        log.info("최종 요청 URL = {}{}", webClient, classifyPath);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path(classifyPath).build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ClassificationResponseVO.class)
                .flatMap(response -> {
                    String predictedAnimal = response.getPredictedAnimal().toLowerCase();
                    double confidence = response.getConfidence();

                    if (confidence < confidenceThreshold) {
                        log.warn("[CLASSIFY FAILED] 낮은 정확도. 예측: {}, 확신도: {}", predictedAnimal, confidence);
                        return Mono.error(new ClassificationFailedException(
                                String.format("이미지 분류 정확도(%.1f%%)가 낮아 캐릭터를 생성할 수 없습니다. 다른 이미지를 시도해 주세요.", confidence * 100)
                        ));
                    }

                    log.info("[CLASSIFY SUCCESS] 예측: {}, 확신도: {}", predictedAnimal, confidence);
                    return Mono.just(predictedAnimal);
                })
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    if (e instanceof ClassificationFailedException) {
                        return Mono.error(e);
                    }
                    String msg = String.format("모델 서버 통신 오류: %s (엔드포인트: %s)", e.getMessage(), classifyPath);
                    log.error(msg);
                    return Mono.error(new RuntimeException("이미지 분류 서버에 연결할 수 없거나 응답이 잘못되었습니다. (설정값 확인 요망)"));
                });
    }
}
