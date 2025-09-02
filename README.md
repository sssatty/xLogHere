open folder in cmd.

set env var.

`set JAVAFX_LIB="C:\javafx-sdk-21.0.7\lib"`

compile with javac

`javac --module-path "%JAVAFX_LIB%" --add-modules javafx.controls -classpath ".;sqlite-jdbc-3.50.3.0.jar" Main.java`

run `run.bat`
