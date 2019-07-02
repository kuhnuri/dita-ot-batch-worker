#!/usr/bin/env sh

CLASSPATH=conf/
for i in lib/*.jar; do
    CLASSPATH=$CLASSPATH:$i
done
. /opt/app/config/env.sh

echo "Environment variables:"
env
echo "Arguments"
echo $@

java $JAVA_OPTS -cp $CLASSPATH com.elovirta.kuhnuri.Main $@

STATUS=$?
echo "Exit status: $STATUS"
exit $STATUS
