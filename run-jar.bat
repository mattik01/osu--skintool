@echo off
echo Running osu! Skin Tool from JAR...

REM Build JAR if it doesn't exist
if not exist "target\osu-skintool-1.0.0.jar" (
    echo Building JAR file...
    call mvn clean package -DskipTests
)

REM Run the JAR with optimized JVM settings
java ^
    -Xms512m -Xmx2048m ^
    -XX:+UseG1GC ^
    -XX:MaxGCPauseMillis=50 ^
    -XX:+UseStringDeduplication ^
    -Dprism.order=d3d,sw ^
    -Dprism.vsync=false ^
    -Djavafx.animation.pulse=60 ^
    -Dfile.encoding=UTF-8 ^
    -jar target\osu-skintool-1.0.0.jar

pause