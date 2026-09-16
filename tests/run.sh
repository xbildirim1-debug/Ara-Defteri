#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BUILD_DIR=$(mktemp -d)
trap 'rm -rf "$BUILD_DIR"' EXIT
java com.sun.tools.javac.Main -d "$BUILD_DIR" \
 app/src/main/java/com/aracdefteri/app/{RecordParser,SmartDocumentAnalyzer,DocumentLayout,OilCardParser,VoiceNumbers,BodyDiagramReader,ObdProtocol,TurkeyVehicleSpecs,TurkeyVehicleSpecsExtra,TurkeyVehicleCatalog}.java \
 tests/com/aracdefteri/app/*.java
java -cp "$BUILD_DIR" com.aracdefteri.app.InputRegressionTest "${@}"
java -cp "$BUILD_DIR" com.aracdefteri.app.ObdProtocolTest
