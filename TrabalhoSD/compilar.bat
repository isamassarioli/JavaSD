@echo off
chcp 65001 > nul
setlocal
cd /d "%~dp0"

if not exist target\classes mkdir target\classes
dir /s /b src\main\java\*.java > target\sources.txt

echo Compilando...
javac -encoding UTF-8 -d target\classes @target\sources.txt
if errorlevel 1 (
    echo.
    echo FALHA na compilacao.
    exit /b 1
)
echo Compilacao concluida em target\classes
endlocal
