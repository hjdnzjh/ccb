FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# 构建应用
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 从构建阶段复制jar文件
COPY --from=builder /app/target/water-meter-service-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]