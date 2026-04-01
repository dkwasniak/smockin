#!/bin/sh

echo "Starting H2 TCP server on port 9092..."
exec java -cp /h2/h2.jar org.h2.tools.Server -tcp -tcpAllowOthers -tcpPort 9092 -baseDir /h2/data -ifNotExists
