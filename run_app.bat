@echo off
set JAVAFX_LIB=javafx-sdk-21.0.7\lib
java --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.graphics,javafx.base -classpath ".;sqlite-jdbc-3.50.3.0.jar" Main
