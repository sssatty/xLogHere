@echo off
REM adjust if your sdk folder name differs
set BASEDIR=%~dp0
set JAVAFX_LIB=%BASEDIR%javafx-sdk-21.0.7\lib
set CP=%BASEDIR%;%BASEDIR%sqlite-jdbc-3.50.3.0.jar

REM Run the program with JavaFX module path and sqlite on classpath
java --module-path "%JAVAFX_LIB%" --add-modules javafx.controls -cp "%CP%" Main
pause
