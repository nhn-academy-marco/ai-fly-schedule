# Eclipse Temurin 21 JRE 기반 이미지 (openjdk 공식 이미지는 더 이상 관리되지 않음)
FROM eclipse-temurin:21-jre

# 작업 디렉토리 설정
WORKDIR /app

# JAR 파일 복사
COPY target/flyschedule-0.0.1-SNAPSHOT.jar app.jar

# 실행 권한 설정
RUN chmod +x app.jar

# 포트 노출
EXPOSE 8080

# JVM 옵션 설정 (메모리 최적화)
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
