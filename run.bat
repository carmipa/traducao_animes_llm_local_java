@echo off
chcp 65001 >nul
REM Inicia o tradutor com console interativo (teclado repassado ao Java).
call gradlew.bat bootRun --console=plain %*
