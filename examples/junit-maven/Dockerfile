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

FROM maven:3-eclipse-temurin-26-noble
COPY --from=agent /src/modules/agent/target/verbatime-agent.jar /work/verbatime-agent.jar
COPY examples/workload /work/workload
COPY examples/junit-maven /work/junit-maven
WORKDIR /work/junit-maven
