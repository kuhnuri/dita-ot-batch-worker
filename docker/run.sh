#!/usr/bin/env sh

CLASSPATH=conf/
for i in lib/*.jar; do
    CLASSPATH=$CLASSPATH:$i
done
PLAY_OPTS="-Duser.dir=/opt/app"
if [ ! -z "$ENVIRONMENT" ]; then
    PLAY_OPTS="$PLAY_OPTS -Dconfig.file=conf/$ENVIRONMENT.conf"
fi
if [ ! -z "$APPLICATION_SECRET" ]; then
    PLAY_OPTS="$PLAY_OPTS -Dplay.http.secret.key=$APPLICATION_SECRET"
fi
. /opt/app/config/env.sh
if [ -f /opt/app/RUNNING_PID ]; then
    rm /opt/app/RUNNING_PID
fi

echo "Environment variables:"
env

java $JAVA_OPTS $PLAY_OPTS -cp $CLASSPATH play.core.server.ProdServerStart

STATUS=$?
echo "Exit status: $STATUS"
exit $STATUS
