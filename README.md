JVM Attach Protocol Wrapper
=========

JVM Attach Protocol allow diagnostic tools send various commands to JVM identified by PID.
Exact attach protocol is platform dependend, but standarized can comatible between JVM versions.


Usage of JVM Attach Protocol requires `tools.jar` in class path.


This project wraps API available via `tools.jar` with some helper code to

 - add `tools.jar` to classpath automatically
 - provide timeouts for command invocation
 - offer utility to parse JVM command output (e.g. heap histogram or stack trace)


In addition, API for HotSpot JVM perf counter is also included.
