START "Window title" /wait cmd /c Compile.bat
java -cp target/;externalJars/* main.Application
PAUSE