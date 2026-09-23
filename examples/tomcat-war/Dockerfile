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
COPY examples/tomcat-war examples/tomcat-war
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/tomcat-war/pom.xml package -DskipTests

FROM tomcat:11.0.25-jdk25-temurin-noble
COPY --from=agent /src/modules/agent/target/verbatime-agent.jar /usr/local/tomcat/verbatime-agent.jar
COPY --from=app /src/examples/tomcat-war/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080 7091
