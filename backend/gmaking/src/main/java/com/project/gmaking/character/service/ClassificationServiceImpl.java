package com.project.gmaking.character.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationServiceImpl implements ClassificationService {

    @Qualifier("classificationWebClient")
    private final WebClient classificationWebClient;

    @Value("${model.server.classify.path}")
    private String classifyPath;

    @Override
    public Mono<String> classifyImage(MultipartFile imageFile) throws IOException {

        log.info("🛰️ 이미지 분류 요청 시작 → {}{}",
                System.getenv("MODEL_SERVER_URL"), classifyPath);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", imageFile.getResource())
                .filename(imageFile.getOriginalFilename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return classificationWebClient.post()
                .uri(classifyPath)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("❌ 모델 서버 통신 오류: {}", e.getMessage()));
    }
}

