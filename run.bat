@echo off
@echo "usage: run.bat start|stop|restart"

::: jar 文件
@set APP=cus-0.0.1-SNAPSHOT.jar
::: 启动 jar 的jvm 参数
@set JVM_ARGS=-Xms215m
::: jar 程序参数
@set PROGRAM_ARGS="-Dlogging.file=logs/out.log --server.port=8308 --spring.profiles.active=dev"

@set START_ARGS=java -jar JarRunner.jar %1 %APP%
::: set START_ARGS=java -jar JarRunner.jar %1 %APP% %JVM_ARGS% {#} %PROGRAM_ARGS%
if %1 == "start" (
  set START_ARGS=%START_ARGS% %JVM_ARGS% {#} %PROGRAM_ARGS%
)
@echo %START_ARGS%

::: start %START_ARGS%
%START_ARGS%
