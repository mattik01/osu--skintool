@echo off
echo Building and running osu! Skin Tool with optimizations...

REM Check if we need to compile
if not exist "target\classes" (
    echo First run detected, compiling...
    call mvn clean compile
)

REM Use Maven to get the classpath
echo Getting classpath from Maven...
call mvn dependency:build-classpath -Dmdep.outputFile=target\classpath.txt -q

REM Run with Maven's JavaFX plugin but with our optimizations
call mvn javafx:run ^
    -Djavafx.args="-Xms512m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -Dprism.order=d3d,sw -Dprism.vsync=false -Djavafx.animation.pulse=60"

pause