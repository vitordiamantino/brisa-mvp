@echo off
cd /d "%~dp0"
if not exist config-local.properties (
  echo Falta o arquivo config-local.properties. Copie o config-local.exemplo.properties e preencha.
  pause
  exit /b 1
)
echo Iniciando a Brisa. Quando aparecer "Started BrisaApplication", abra http://localhost:8080
call mvnw.cmd -q spring-boot:run
pause
