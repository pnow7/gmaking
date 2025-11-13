# Troubleshooting: Spring WebClient + multipart + Docker hostname  

## Error: `Host is not specified`

## 1. 문제 요약 (Symptom)

Spring Boot에서 Docker 내부 FastAPI 서버로 `multipart/form-data` 요청을 보낼 때 다음 오류가 발생:

java.lang.IllegalArgumentException: host is not specified

Spring 로그 예시: ❌ 모델 서버 통신 오류: Host is not specified (엔드포인트: /classify/image)


클라이언트 응답: 이미지 분류 서버에 연결할 수 없습니다. 다시 시도해 주세요.


---

## 2. 문제 재현 조건 (Reproduce Conditions)

아래 조건이 모두 충족될 때 안정적으로 재현됨:

1. Spring WebClient 사용
2. BodyInserters.fromMultipartData 로 multipart/form-data 전송
3. `.uri("http://ai_server:8000/…")` 처럼 문자열 전체 URL 전달
4. WebClientConfig 에 baseUrl 없음
5. Docker hostname(ai_server) 사용

이 조합에서 Reactor Netty 내부에서 Host 헤더가 null 이 되어 오류가 발생함.

---

## 3. 원인 (Root Cause)

### Reactor Netty + WebClient multipart 처리 중 **Host 헤더가 유실되는 버그**

WebClient가 multipart/form-data 인코딩 준비 과정에서  
요청 생성 타이밍이 어긋나며 Host 헤더가 null이 되어 아래 예외가 발생함: IllegalArgumentException: host is not specified


이 문제는 Docker hostname을 사용할 때 더 쉽게 재현되며,  
Docker 네트워크·DNS·FastAPI 문제와는 무관함.

---

## 4. 문제 분석 증거 (Evidence)

아래 테스트 모두 정상 작동:

- `ping ai_server` → 정상
- `curl http://ai_server:8000/docs` → 정상
- FastAPI 모델 서버 정상
- Docker 네트워크 정상
- multipart 제외 시 요청 정상

즉, 네트워크 계층 문제 없음 → WebClient 요청 생성 과정만 오류.

---

## 5. 해결 방법 (Solution)

### ✔ 해결 방법 1 — WebClientConfig 에 baseUrl 추가 (가장 정석, 권장)

```java
@Configuration
public class WebClientConfig {

    @Value("${model.server.url}")
    private String modelServerUrl;

    @Bean
    public WebClient customWebClient() {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(60)));

        return WebClient.builder()
                .baseUrl(modelServerUrl)  // Host 헤더 자동 생성
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

```

### ✔ 해결 방법 2 — Service 단에서 전체 URL 사용 금지
기존 (문제 발생)

```java
.uri(modelServerUrl + classifyPath)
```

수정 (정상 작동)
```java
.uri(classifyPath)
```

이제 baseUrl + path 형태로 Host 헤더가 자동 구성됨.

---

## 6. 참고용 대안 (비권장)
아래 방법도 동작은 하나 장기적으로 비추천:

1) Host 헤더 강제 추가
```java
.header(HttpHeaders.HOST, "ai_server:8000")
```

2) URI.create() 사용
```java
.uri(URI.create(modelServerUrl + classifyPath))
```

baseUrl 방식이 가장 안전하고 Spring 공식 문서에서도 권장됨.

---

## 7. 결론
Docker, FastAPI, 네트워크 문제가 아니다.

Reactor Netty 와 WebClient multipart/form-data 조합에서 Host 헤더가 유실되는 구조적 버그다.

해결 방법은 WebClientConfig 에 baseUrl 추가 + Service 단에서는 path만 전달하는 것이다.

이 두 수정만으로 오류는 완전히 해결된다.

## 8. 적용 후 기대 결과

패치 적용 후:

- FastAPI 모델 서버 통신 정상
- multipart 이미지 업로드 정상
- Host is not specified 오류 제거
- 이미지 분류 기능 정상 작동

정상 응답 예시:
```yaml
🧩 모델 서버 응답 VO: { predictedAnimal=..., confidence=0.95 }
```