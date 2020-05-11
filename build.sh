#!/bin/sh

echo "Building..."
# Make dir for ts3 musicbot .jar file
mkdir -p out/artifacts/ts3_musicbot
# Build ts3 music bot
kotlinc -cp $(echo lib/*.jar | tr " " ":") -include-runtime src/main -d out/artifacts/ts3_musicbot/ts3-musicbot.jar
echo "Adding manifest file..."
# Add manifest file
jar -ufm out/artifacts/ts3_musicbot/ts3-musicbot.jar src/META-INF/MANIFEST.MF 2> /dev/null
# Include libraries
mkdir -p .temp/lib
echo "Extracting libraries from lib..."
unzip -q lib/*.jar -d .temp/lib
echo "Removing unnecessary MANIFEST.MF file(s)..."
rm -f .temp/lib/META-INF/MANIFEST.MF
echo "Adding library files to .jar..."
jar -uf out/artifacts/ts3_musicbot/ts3-musicbot.jar -C .temp/lib .
echo "Removing temporary files..."
rm -rf .temp
echo "Done."
