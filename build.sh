#!/bin/sh

mkdir -p out/artifacts/ts3_musicbot
kotlinc -include-runtime src -d out/artifacts/ts3_musicbot/ts3-musicbot.jar
jar -ufm out/artifacts/ts3_musicbot/ts3-musicbot.jar src/META-INF/MANIFEST.MF 2> /dev/null
