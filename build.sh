#!/bin/sh

# Make dir for ts3 musicbot .jar file
mkdir -p out/artifacts/ts3_musicbot
# Build ts3 music bot
kotlinc -cp $(echo lib/*.jar | tr " " ":") -include-runtime src -d out/artifacts/ts3_musicbot/ts3-musicbot.jar 
# Add manifest file
jar -ufm out/artifacts/ts3_musicbot/ts3-musicbot.jar src/META-INF/MANIFEST.MF 2> /dev/null
# Include libraries
mkdir -p .temp/lib
unzip -q -d .temp/lib lib/*
rm .temp/lib/META-INF/MANIFEST.MF
jar -uf out/artifacts/ts3_musicbot/ts3-musicbot.jar -C .temp/lib .
rm -rf .temp
