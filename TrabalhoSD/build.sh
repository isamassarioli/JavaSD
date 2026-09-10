#!/usr/bin/env bash
# Scripts equivalentes para Linux / macOS.
#   ./build.sh compilar
#   ./build.sh servidor [porta]
#   ./build.sh cliente  [host] [porta]
#   ./build.sh teste
set -e
cd "$(dirname "$0")"

CP=target/classes
MAIN=br.ifes.sin.sd.recomendacao.App

compilar() {
    mkdir -p "$CP"
    find src/main/java -name '*.java' > target/sources.txt
    javac -encoding UTF-8 -d "$CP" @target/sources.txt
    echo "Compilacao concluida em $CP"
}

case "${1:-}" in
    compilar) compilar ;;
    servidor) shift; java -Dfile.encoding=UTF-8 -cp "$CP" "$MAIN" servidor "$@" ;;
    cliente)  shift; java -Dfile.encoding=UTF-8 -cp "$CP" "$MAIN" cliente "$@" ;;
    teste)    java -Dfile.encoding=UTF-8 -cp "$CP" "$MAIN" teste ;;
    *) echo "Uso: $0 {compilar|servidor [porta]|cliente [host] [porta]|teste}" ; exit 1 ;;
esac
