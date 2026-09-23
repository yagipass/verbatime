# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-26-noble AS agent
WORKDIR /src
COPY pom.xml LICENSE NOTICE ./
COPY .mvn .mvn
COPY modules/jmc/pom.xml modules/jmc/pom.xml
COPY modules/cli/pom.xml modules/cli/pom.xml
COPY modules/format modules/format
COPY modules/agent modules/agent
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -pl modules/agent -am package -DskipTests -Dmaven.test.skip=true -Dmaven.antrun.skip=true

FROM maven:3-eclipse-temurin-26-noble AS app
WORKDIR /src
COPY examples/workload examples/workload
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/workload/pom.xml install -DskipTests
COPY examples/spring-batch examples/spring-batch
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/spring-batch/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=agent /src/modules/agent/target/verbatime-agent.jar /app/verbatime-agent.jar
COPY --from=app /src/examples/spring-batch/target/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
