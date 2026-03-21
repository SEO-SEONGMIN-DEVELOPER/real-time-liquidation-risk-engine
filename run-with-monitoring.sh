#!/bin/bash
# G1 GC + JMX 활성화하여 애플리케이션 실행 (JConsole STW 모니터링용)

set -e
cd "$(dirname "$0")"

JAR=$(find build/libs -name '*.jar' -not -name '*-plain.jar' | head -1)
if [ -z "$JAR" ]; then
  echo "JAR not found. Run: ./gradlew bootJar"
  exit 1
fi

java \
  -XX:+UseG1GC \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -jar "$JAR" "$@"
