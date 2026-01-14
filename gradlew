#!/bin/sh

##############################################################################
#
#   Gradle start up script for POSIX
#
##############################################################################

# Attempt to set APP_HOME
app_path=$0
APP_HOME=$( cd "${app_path%"${app_path##*/}"}" && pwd -P ) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

# Check for Java
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command found in PATH."
    exit 1
fi

exec "$JAVACMD" \
    -Xmx64m -Xms64m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
