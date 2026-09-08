# ============================================================
# Stage 1: BUILD
# Dùng Maven image để compile và package JAR
# Kết quả: target/cdms-0.0.1-SNAPSHOT.jar
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml TRƯỚC — tận dụng Docker layer cache:
# Nếu chỉ source code thay đổi (không đổi pom.xml),
# Docker bỏ qua bước download dependency → build nhanh hơn nhiều
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code và build JAR (skip test — test chạy riêng)
COPY src ./src
RUN mvn package -DskipTests -q

# ============================================================
# Stage 2: RUNTIME
# Chỉ copy JAR vào JRE image nhỏ hơn (không có Maven, src code)
# eclipse-temurin:21-jre ~ 250MB vs maven:3.9 ~ 700MB
# ============================================================
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Tạo non-root user — security best practice
# Chạy với root trong container là rủi ro bảo mật
RUN groupadd --system cdms && \
    useradd --system --gid cdms --no-create-home cdms

# Copy JAR từ stage builder
COPY --from=builder /build/target/cdms-0.0.1-SNAPSHOT.jar app.jar

# Đổi owner về non-root user
RUN chown cdms:cdms app.jar

USER cdms

# Port mà Spring Boot lắng nghe
EXPOSE 8080

# JVM tuning cho container environment:
# -XX:+UseContainerSupport   → JVM đọc CPU/Memory limit từ Docker, không từ host
# -XX:MaxRAMPercentage=75.0  → dùng tối đa 75% RAM container cho JVM heap
# -Djava.security.egd=...    → tăng tốc startup bằng non-blocking random source
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]