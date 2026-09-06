# ── build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /build

# 의존성만 먼저 받아 레이어 캐싱. 소스만 바뀌면 이 단계는 재사용됨
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# 테스트는 Testcontainers 로 Docker 를 요구하므로 이미지 빌드 안에서 못 돌림
# CI(.github/workflows/build.yml) 에서 이미 수행함
RUN ./gradlew bootJar --no-daemon -x test

# ── run ─────────────────────────────────────────────────────────────
# alpine 변형은 오래된 alpine:3 베이스를 물고 있어 Critical 2 / High 9
# noble 은 동일 시점 기준 Critical 0 / High 0 (크기는 208MB -> 312MB)
FROM eclipse-temurin:21-jre-noble
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app

COPY --from=builder /build/build/libs/*.jar app.jar
USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 프로파일은 ENTRYPOINT 에 박지 않음. 같은 이미지를 로컬/EKS 에서 재사용하려면 환경변수여야 함
# -Xmx 고정 대신 MaxRAMPercentage 로 컨테이너 메모리 제한을 따라감
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
