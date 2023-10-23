## JarRunner
启动或停止 jar 文件的小程序  
~~主要是对 `bat` 和 `shell` 不熟~~ （不是）

### Intro
`jdk`8+ ，且已添加到环境变量  
利用 `java` 运行环境内置的 `jps` 命令读取和判断程序的运行，
目前仅在 `windows` 下测试

### start
`{#}` 分隔 jvm参数和程序参数  

java -Xms215m -Xmx1024m -jar mycus.jar
```
java -jar JarRunner.jar start mycus.jar -Xms215m -Xmx1024m {#}
```

java -jar mycus.jar -Dlogging.file=./out.log --server.port=8308
```
java -jar JarRunner.jar start mycus.jar {#} -Dlogging.file=./out.log --server.port=8308
```

java -Xms215m -Xmx1024m -jar mycus.jar -Dlogging.file=./out.log --server.port=8308
```
java -jar JarRunner.jar start mycus.jar -Xms215m -Xmx1024m {#} -Dlogging.file=./out.log --server.port=8308
```

### stop
（windows如遇stop不能正常工作，请用管理员身份执行）
```
java -jar JarRunner.jar stop mycus.jar

# with force
java -jar JarRunner.jar stop mycus.jar -f 
```

### restart
同 `start`
```
java -jar JarRunner.jar restart ....
```
### bat
使用脚本简化， 创建 `run.bat`，内容如下
```bat
@echo off
echo "usage: run.bat start|stop|restart"

::: jar file
set APP=mycus-SNAPSHOT.jar
::: jar's jvm options
set JVM_ARGS=-Xms215m
::: jar's program arguments
set PROGRAM_ARGS=-Dlogging.file=logs/out.log --server.port=8308 --spring.profiles.active=dev

set START_ARGS=java -jar JarRunner.jar %1 %APP%
::: set START_ARGS=java -jar JarRunner.jar %1 %APP% %JVM_ARGS% {#} %PROGRAM_ARGS%
if "%1" == "start" (
  goto appendArgs
) else if "%1" == "restart" (
  goto appendArgs
) else if "%1" == "stop" (
  if exist "%2"!="" (
    set START_ARGS=%START_ARGS% %2
  )
) else (
  goto execStart
)

::: 添加启动参数
:appendArgs
set START_ARGS=%START_ARGS% %JVM_ARGS% {#} %PROGRAM_ARGS%

::: 执行
:execStart
echo %START_ARGS%

::: start %START_ARGS%
%START_ARGS%
PAUSE
```
使用方式（如遇stop不能正常工作，请用管理员身份执行）：
```
run.bat "start"|"restart"
run.bat stop [-f]
```

