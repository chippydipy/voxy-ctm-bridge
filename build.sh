#!/bin/sh
# Builds build/voxy-ctm-bridge-<version>.jar with plain javac (JDK 25 required).
# Needs classpath.txt: one line, jar paths separated by ':' (see README, "Building").
set -e
VERSION=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' src/main/resources/fabric.mod.json | head -n1)
[ -f classpath.txt ] || { echo "classpath.txt not found - see README, 'Building'"; exit 1; }
rm -rf build && mkdir -p build/classes build/jar
javac --release 25 -proc:none -d build/classes -cp "$(cat classpath.txt)" $(find src/main/java -name '*.java')
cp -r build/classes/* build/jar/
cp src/main/resources/* build/jar/
cp LICENSE build/jar/LICENSE_voxy_ctm_bridge
jar --create --file "build/voxy-ctm-bridge-$VERSION.jar" -C build/jar .
echo "Built build/voxy-ctm-bridge-$VERSION.jar"
