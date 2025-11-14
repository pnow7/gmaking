# syntax=docker/dockerfile:1

### --- Build Stage ------------------------------------------------------
FROM gradle:8.10.0-jdk17 AS builder
WORKDIR /app

# Gradle 설정 파일 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# 의존성 캐싱 (옵션)
RUN gradle dependencies --no-daemon || return 0

# 전체 소스 복사
COPY . .

# JAR 빌드
RUN ./gradlew clean build -x test --no-daemon


### --- Runtime Stage ----------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# 네트워크 유틸 설치 (선택)
RUN apt-get update && apt-get install -y dnsutils iputils-ping curl

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/gmaking-0.0.1-SNAPSHOT.jar app.jar

# entrypoint 스크립트
COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

# 컨테이너 포트
EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
