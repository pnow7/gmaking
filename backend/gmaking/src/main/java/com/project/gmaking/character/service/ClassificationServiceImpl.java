package com.project.gmaking.character.service;

import com.project.gmaking.character.exception.ClassificationFailedException;
import com.project.gmaking.character.vo.ClassificationResponseVO;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final WebClient customWebClient;
    private final String modelServerUrl;

    @Value("${model.server.classify.path}")
    private String classifyPath;

    @Value("${classification.threshold:0.80}")
    private double confidenceThreshold;

    public ClassificationServiceImpl(
            @Qualifier("classificationWebClient") WebClient classificationWebClient,
            @Value("${model.server.url}") String modelServerUrl
    ) {
        this.modelServerUrl = modelServerUrl;
        this.customWebClient = classificationWebClient;

        log.info("### DEBUG: Injected modelServerUrl = [{}]", modelServerUrl);
        log.info("### Injected WebClient Bean = classificationWebClient");
    }

    @Override
    public Mono<String> classifyImage(MultipartFile imageFile) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", imageFile.getResource());

        log.info("🛰️ 이미지 분류 요청 시작 → 엔드포인트: {}", classifyPath);

        return customWebClient.post()
                .uri(classifyPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                // ----------------------------------------------------
                // 1. 응답 파싱: String 대신 ClassificationResponseVO 클래스로 받도록 변경
                .bodyToMono(ClassificationResponseVO.class)
                .doOnNext(response -> log.info("🧩 모델 서버 응답 VO: {}", response))

                // 2. 데이터 처리: VO에서 필요한 값 추출 및 신뢰도 검사
                .map(response -> {
                    // 예측 신뢰도 검사
                    if (response.getConfidence() >= confidenceThreshold) {
                        // 신뢰도 통과 시 예측된 동물 이름 반환
                        return response.getPredictedAnimal();
                    } else {
                        // 신뢰도 임계값 미달 시 예외 발생
                        String errorMsg = String.format("❌ 예측 신뢰도 임계값 미달: %.4f (임계값: %.2f)",
                                response.getConfidence(), confidenceThreshold);
                        log.warn(errorMsg);
                        // ClassificationFailedException은 VO 패키지에 정의되어 있습니다.
                        throw new ClassificationFailedException("이미지 분류 신뢰도 부족: " + response.getPredictedAnimal());
                    }
                })
                // ----------------------------------------------------
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    String msg = String.format("❌ 모델 서버 통신 오류: %s (엔드포인트: %s)", e.getMessage(), classifyPath);
                    log.error(msg);
                    return Mono.error(new RuntimeException("이미지 분류 서버에 연결할 수 없습니다. 다시 시도해 주세요.", e));
                });
    }

}
