@echo off
echo "usage: run.bat start|stop|restart"

::: jar 文件
set APP=mycus-SNAPSHOT.jar
::: 启动 jar 的jvm 参数
set JVM_ARGS=-Xms215m
::: jar 程序参数
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