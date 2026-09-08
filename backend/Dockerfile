FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*-all.jar /app/app.jar
EXPOSE 8080
ENV PORT=8080
CMD ["java", "-jar", "/app/app.jar"]
