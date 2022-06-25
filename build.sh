#!/bin/sh

# Make dir for ts3 musicbot .jar file
mkdir -p out/artifacts/ts3_musicbot
# Build using gradle and if successful, copy the .jar file to the output directory.
./gradlew assemble "$@" && echo "Copying ts3-musicbot.jar to out/articats/ts3_musicbot/" &&
  cp build/libs/ts3-musicbot.jar out/artifacts/ts3_musicbot/. && echo "Done."
