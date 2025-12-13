#!/usr/bin/env sh
##############################################################################
# Gradle start up script for UN*X
#
# This file is a minimal wrapper that expects the Gradle Wrapper JAR to exist
# at `./gradle/wrapper/gradle-wrapper.jar`. If that JAR is missing, run a
# local Gradle to generate the wrapper or place the JAR into that path.
##############################################################################

BASEDIR=$(dirname "$0")
WRAPPER_JAR="$BASEDIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: gradle-wrapper.jar not found at $WRAPPER_JAR"
  echo "Run: gradle wrapper --gradle-version 8.4.1" 
  exit 1
fi

exec java -jar "$WRAPPER_JAR" "$@"
#!/bin/sh
DIR="C:\Users\dlwjd\IdeaProjects\telegramalramrelay/"
if [ -n "" ]; then
  JAVA_CMD="/bin/java"
else
  JAVA_CMD=java
fi
exec "" -cp "/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
