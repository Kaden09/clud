FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.31.1/opentelemetry-javaagent.jar /workspace/otel.jar

FROM eclipse-temurin:21-jre

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && groupadd --gid 10001 gateway \
  && useradd --uid 10001 --gid gateway --no-create-home --no-log-init --shell /usr/sbin/nologin gateway \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder --chown=gateway:gateway /workspace/target/*.jar app.jar

COPY --from=builder --chown=gateway:gateway /workspace/otel.jar otel.jar

USER gateway:gateway
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:otel.jar", "-jar", "app.jar"]
