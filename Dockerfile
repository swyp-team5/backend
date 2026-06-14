FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ENV TZ=Asia/Seoul

COPY --from=build /workspace/build/libs/*.jar /app/autoschedule.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/autoschedule.jar"]
