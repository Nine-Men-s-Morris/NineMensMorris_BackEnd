FROM openjdk:17-alpine
WORKDIR /app
COPY . .
RUN ./gradlew build -x test
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]