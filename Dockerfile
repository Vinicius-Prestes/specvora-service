# =========================================================================
# Multi-stage Dockerfile seguro para DevSecOps
# 1. Menor superfície de ataque (apenas JRE necessário)
# 2. Execução como usuário não-root (princípio do menor privilégio)
# =========================================================================

# Estágio de Runtime Seguro
FROM eclipse-temurin:21-jre-jammy

# Criar grupo e usuário sem privilégios administrativos
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 appuser

WORKDIR /app

# Copiar apenas o arquivo JAR gerado no build
COPY target/*.jar app.jar

# Ajustar propriedade dos arquivos para o usuário seguro
RUN chown -R appuser:appgroup /app

# Executar como usuário não-root
USER appuser

# Expor porta da aplicação (definida no Spring Boot)
EXPOSE 8080

# Configurações de JVM seguras e otimizadas para containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
