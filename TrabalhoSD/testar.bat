@echo off
chcp 65001 > nul
cd /d "%~dp0"
if not exist target\classes (
    echo Compile antes com: compilar.bat
    exit /b 1
)
java -Dfile.encoding=UTF-8 -cp target\classes br.ifes.sin.sd.recomendacao.App teste
