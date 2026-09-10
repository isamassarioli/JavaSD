@echo off
chcp 65001 > nul
cd /d "%~dp0"
if not exist target\classes (
    echo Compile antes com: compilar.bat
    exit /b 1
)
rem Uso: servidor.bat [porta]   (porta padrao: 5000)
java -Dfile.encoding=UTF-8 -cp target\classes br.ifes.sin.sd.recomendacao.App servidor %1
