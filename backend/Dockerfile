FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle gradle
COPY gradlew gradlew
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/install/meditation-hall-api /app
EXPOSE 8080
ENV PORT=8080
CMD ["/app/bin/meditation-hall-api"]
