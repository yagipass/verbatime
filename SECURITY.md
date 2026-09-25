# Security policy

## Supported versions

Only the latest release gets security fixes.

## Reporting a vulnerability

Do not open a public issue. Send a private report from
[Report a vulnerability](https://github.com/yagipass/verbatime/security/advisories/new).

## Scope

Verbatime is a tool for development. The agent opens no network port. The JMX port that
JDK Mission Control connects to comes from the JVM's own `com.sun.management.jmxremote.*`
flags, and the docs turn its authentication off for use on a development machine.
