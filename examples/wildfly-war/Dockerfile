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

FROM maven:3.9.16-eclipse-temurin-25-noble AS app
WORKDIR /src
COPY examples/workload examples/workload
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/workload/pom.xml install -DskipTests
COPY examples/tomcat-war examples/tomcat-war
RUN --mount=type=cache,id=verbatime-m2,target=/root/.m2/repository,sharing=locked \
    mvn -B -f examples/tomcat-war/pom.xml package -DskipTests

FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk25
COPY --from=agent --chown=jboss:root /src/modules/agent/target/verbatime-agent.jar /opt/jboss/wildfly/verbatime-agent.jar
COPY --from=app --chown=jboss:root /src/examples/tomcat-war/target/ROOT.war /opt/jboss/wildfly/standalone/deployments/ROOT.war
RUN set -eu; B=/opt/jboss/wildfly/modules/system/layers/base; \
    CP=$(ls $B/org/jboss/logmanager/main/*.jar $B/org/wildfly/common/main/*.jar $B/io/smallrye/common/*/main/*.jar $B/org/jboss/logging/main/*.jar | tr '\n' ':'); \
    printf '\nJAVA_OPTS="$JAVA_OPTS -Xbootclasspath/a:%s -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dsun.util.logging.disableCallerCheck=true"\n' "${CP%:}" >> /opt/jboss/wildfly/bin/standalone.conf
EXPOSE 8080 7091
