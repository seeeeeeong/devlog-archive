FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY build/libs/devlog-archive-*.jar app.jar

ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
