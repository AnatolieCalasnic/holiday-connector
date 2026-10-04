FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar
USER app
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
