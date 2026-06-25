@echo off
REM Inicia o tradutor com console interativo (teclado repassado ao Java).
call gradlew.bat bootRun --console=plain %*
