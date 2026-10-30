# Security

## Reporting a vulnerability

Report vulnerabilities privately through
[GitHub's private vulnerability reporting](https://github.com/yagipass/verbatime/security/advisories/new).
Do not open a public issue.

## Supported versions

Only the latest release receives security fixes.

## Scope

Verbatime is a tool for development. The agent opens no network port. The JMX port that
JDK Mission Control connects to comes from the JVM's own `com.sun.management.jmxremote.*`
flags, and the docs turn its authentication off for use on a development machine.
