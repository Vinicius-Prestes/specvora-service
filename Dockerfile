# syntax=docker/dockerfile:1.7
# =========================================================================
# Multi-stage Dockerfile Seguro para DevSecOps
# 1. Estágio de Build (JDK 21) isolado do ambiente final
# 2. Estágio de Runtime enxuto (JRE 21) sem ferramentas de compilação
# 3. Usuário não-root (UID 10001) para menor privilégio
# 4. Entrypoint em forma exec (PID 1 direto para graceful shutdown)
# 5. Healthcheck nativo
# =========================================================================

# Estágio 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Cache de dependências Maven
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Compilação e empacotamento
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# Estágio 2: Runtime Seguro
FROM eclipse-temurin:21-jre-jammy

# Criar grupo e usuário sem privilégios administrativos
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 -s /usr/sbin/nologin appuser

WORKDIR /app

# Copiar o JAR gerado no estágio builder com propriedade do appuser
COPY --from=builder --chown=10001:10001 /build/target/*.jar app.jar

USER 10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
