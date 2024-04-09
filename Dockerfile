FROM openjdk:17-alpine
WORKDIR /app
COPY build/libs/test.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Duser.timezone-Asia/Seoul", "-jar", "app.jar"]