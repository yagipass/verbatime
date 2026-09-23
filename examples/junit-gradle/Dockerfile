# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25-noble AS agent
WORKDIR /src
COPY pom.xml LICENSE NOTICE ./
COPY .mvn .mvn
COPY modules/jmc/pom.xml modules/jmc/pom.xml
COPY modules/cli/pom.xml modules/cli/pom.xml
COPY modules/format modules/format
COPY modules/agent modules/agent
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -pl modules/agent -am package -DskipTests -Dmaven.test.skip=true -Dmaven.antrun.skip=true

FROM gradle:9.7.1-jdk25-noble
COPY --from=agent --chown=gradle:gradle /src/modules/agent/target/verbatime-agent.jar /work/verbatime-agent.jar
COPY --chown=gradle:gradle examples/workload /work/workload
COPY --chown=gradle:gradle examples/junit-gradle /work/junit-gradle
WORKDIR /work/junit-gradle
