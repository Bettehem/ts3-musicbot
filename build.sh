#!/bin/sh

# Make dir for ts3 musicbot .jar file
mkdir -p out/artifacts/ts3_musicbot
# Build using gradle and if successful, copy the .jar file to the output directory.
./gradlew assemble "$@" &&
  echo "Copying ts3-musicbot.jar to out/articats/ts3_musicbot/"
cp build/libs/ts3-musicbot.jar out/artifacts/ts3_musicbot/. &&
  printf "Removing unnecessary files... " &&
  zip -q -d out/artifacts/ts3_musicbot/ts3-musicbot.jar com/\* javafx/\* javafx.properties javafx-swt.jar libdecora_sse.so libglass.so libglassgtk2.so libglassgtk3.so libjavafx_font.so libjavafx_font_freetype.so libjavafx_font_pango.so libjavafx_iio.so libprism_common.so libprism_es2.so libprism_sw.so module-info.class kotlin/jdk7/\* kotlin/\*kotlin_metadata META-INF/maven/org.jetbrains/\* META-INF/kotlin-stdlib-common.kotlin_module META-INF/kotlin-stdlib-jdk\*.kotlin_module org/intellij/\* org/jetbrains/\* META-INF/versions/\* kotlin/collections/jdk8/\* kotlin/collections/\*.kotlin_metadata kotlin/collections/UByte\* kotlin/collections/UCollections\* kotlin/collections/UInt\* kotlin/collections/ULong\* kotlin/collections/UShort\* kotlin/comparisons/\*.kotlin_metadata kotlin/contracts/\*.kotlin_metadata kotlin/coroutines/\*.kotlin_metadata kotlin/coroutines/cancellation/\*.kotlin_metadata kotlin/coroutines/intrinsics/\*.kotlin_metadata kotlin/experimental/\*.kotlin_metadata kotlin/internal/jdk\*/\* kotlin/internal/\*.kotlin_metadata kotlin/io/path/\* kotlin/io/\*.kotlin_metadata kotlin/js/\*.kotlin_metadata kotlin/jvm/jdk8/\* kotlin/jvm/\*.kotlin_metadata kotlin/math/\*.kotlin_metadata kotlin/native/\* kotlin/properties/\*.kotlin_metadata kotlin/random/jdk8/\* kotlin/random/\*.kotlin_metadata kotlin/ranges/\*.kotlin_metadata kotlin/reflect/\*.kotlin_metadata kotlin/sequences/\*.kotlin_metadata kotlin/streams/\* kotlin/system kotlin/text/jdk8/\* kotlin/text/\*.kotlin_metadata kotlin/time/jdk8/\*.kotlin_metadata kotlin/time/\*.kotlin_metadata &&
  echo "Done."
