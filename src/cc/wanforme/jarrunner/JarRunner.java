package cc.wanforme.jarrunner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 用于启动和停止 jar 应用程序，
 * windows 下注册服务有点麻烦，
 * 原理则是利用 java 命令 jps -l 获取jar的名称<br/>
 *
 * 参数1 start | stop | restart
 * 参数2 myapp-0.0.1-SNAPSHOT.jar
 * 参数3 附加启动参数。使用 {#} 区分 left 参数 (jvm参数) 和 right 参数 (程序参数)
 * {left} {#} {right}
 * {left} -jar myapp-0.0.1-SNAPSHOT.jar {right}
 *
 * java -jar JarRunner.jar start mycus.jar -Xms215m -Xmx1024m {#}
 * java -jar JarRunner.jar start mycus.jar {#} -Dlogging.file=./out.log --server.port=8308
 * java -jar JarRunner.jar start mycus.jar -Xms215m -Xmx1024m {#} -Dlogging.file=./out.log --server.port=8308
 * java -jar JarRunner.jar stop  mycus.jar [-f]
 *
 * java -jar JarRunner.jar restart mycus.jar -Xms215m -Xmx1024m {#} -Dlogging.file=./out.log --server.port=8308
 *
 * 38704 finemi_framework_2-0.0.1-SNAPSHOT.jar
 * 50240 sun.tools.jps.Jps
 */
public class JarRunner {
    // jar 绝对路径
    private String app;
    // 命令中输入的原始 app 名称
    private String originApp;
    // 启动类型
    private String type;
    // 程序启动参数
    private String[] launchArgs;
    // 存放 pid 的文件
    private File pidLoc;

    public static void main(String[] args) throws IOException {
        //System.out.println("args: " + Arrays.toString(args));
        // 参数检查
        if(!check(args)) {
            return;
        }

        String type = args[0];
        String appArg = args[1];

        String[] launchArgs = null;
        if(args.length > 2) {
            launchArgs = Arrays.copyOfRange(args, 2, args.length);
        }

        JarRunner runner = new JarRunner(type, appArg, launchArgs);
        runner.exec();
    }


    public JarRunner(String type, String app, String[] launchArgs) {
       this.type = type;
       this.originApp = app;

       // 换成全路径，防止出现相同名字，导致冲突
       File appFile = new File(app);
       this.app = appFile.getAbsolutePath();
       if(!appFile.exists()) {
           System.out.println("App file does not existed! " + this.app);
           System.exit(1);
       }

       this.launchArgs = launchArgs;
       this.pidLoc = new File(this.app + ".pid");
    }

    private static boolean check(String[] args) {
        if(args == null || args.length < 2) {
            usage();
            return false;
        }
        String type = args[0];
        if (!"start".equals(type) && !"stop".equals(type) && !"restart".equals(type)) {
            usage();
            return false;
        }
        return true;
    }

    private static void usage() {
        String s = "java -jar JarRunner.jar start|restart xxx.jar \r\n"
                + "java -jar JarRunner.jar start|restart [jvm args] {#} [app args] \r\n"
                + "java -jar JarRunner.jar stop xxx.jar [-f] \r\n\r\n"
                + "example: java -jar JarRunner.jar start xxx.jar -Xmx 215m -Xms 1024m {#} -Dlogging.file=./out.log --server.port=8080";
        System.out.println(s);
    }

    public void exec() throws IOException {
        if ("start".equals(this.type)) {
            // 启动
            if(isRunning()) {
                System.out.println("["+originApp+"] is running at pid [" + loadPid() +"]");
                return;
            }

            startApp();

            delay(1000);
            if(isRunning()) {
                System.out.println("["+originApp+"] is running. [" + loadPid() +"]");
                return;
            }
        } else if ("stop".equals(this.type)) {
            // 检查是否在运行
            if(!isRunning()) {
                System.out.println("["+originApp+"] is not running!");
                return;
            }
            killApp(false);

            delay(1000);
            if(isRunning()) {
                delay(2000);
                if(isRunning() && isForceStop()) {
                    System.out.println("Force kill");
                    killApp(true);
                } else {
                    System.out.println("FAILED! Trying to run 'stop xxx.jar -f' with permission.");
                }
            }
        } else if ("restart".equals(this.type)) {
            // 检查是否在运行
            if(isRunning()) {
                killApp(false);
                if(isRunning()) {
                    System.out.println("force kill");
                    killApp(true);
                }
            }
            startApp();
        }

    }
    // 检查是否在运行中
    private boolean isRunning() throws IOException {
        String pid = loadPid();
        if(pid == null || pid.trim() == "") { // 文件不存在或内容为空
            return false;
        }

        // 检查 pid 是否真的在运行
        Process jps = Runtime.getRuntime().exec("jps");
        String processOutput = getProcessOutput(jps.getInputStream());

        int re;
        try {
            re = jps.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if(re == 0) {
            String[] pids = spliteStr(processOutput);
            for(String p : pids) {
                if(p.startsWith(pid + " ") || Objects.equals(p, pid)) {
                    return true;
                }
            }
            return false;
        } else {
            throw new RuntimeException("Error on querying process!");
        }
    }
    // 是否使用强制停止
    private boolean isForceStop() {
        return launchArgs != null && launchArgs.length > 0 && "-f".equals(launchArgs[0]);
    }
    // 启动进程
    protected void startApp() throws IOException {
        List<String> jvmArgs = new ArrayList<>();
        List<String> programArgs = new ArrayList<>();
        if(launchArgs != null) {
            boolean isLeft = true;
            for (String a: launchArgs) {
                if("{#}".equals(a.trim())) {
                    isLeft = false;
                } else if(isLeft) {
                    jvmArgs.add(a);
                } else {
                    programArgs.add(a);
                }
            }
        }


        List<String> execArgs = new ArrayList<>();
        execArgs.add("java");
        execArgs.addAll(jvmArgs);
        execArgs.add("-jar");
        execArgs.add(app);
        execArgs.addAll(programArgs);

        ProcessBuilder builder = new ProcessBuilder(execArgs.toArray(new String[0]));
        System.out.println("[exec] " + String.join(" ", builder.command()));
        builder.start();

        savePid();
    }
    // 杀死进程
    protected void killApp(boolean force) throws IOException {
        String pid = loadPid();
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder builder = null;
        if (os.contains("win")) {
            if(force) {
                builder = new ProcessBuilder("taskkill", "/f", "/pid", pid);
            } else {
                builder = new ProcessBuilder("taskkill", "/pid", pid);
            }
        } else if (os.contains("nix") || os.contains("nux")) {
            if(force) {
                builder = new ProcessBuilder("kill", "-f", pid);
            } else {
                builder = new ProcessBuilder("kill", pid);
            }
        } else if (os.contains("mac")) {
            // todo
            throw new RuntimeException("it's not supported on mac!");
        }

        if(builder == null) {
            throw new RuntimeException("unknown OS type!");
        }

        System.out.println("[exec] " + String.join(" ", builder.command()));
        Process process = builder.start();
        int re = 0;

        try {
            re = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        System.out.println("stop status: " + re);
        if(re == 0) {
            System.out.println( "["+originApp + "] has been stopped!");
        }
    }

    // 获取执行结果
    private String getProcessOutput(InputStream processOutput) throws IOException {
        // 读取命令执行结果
        BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }
    private String[] spliteStr(String str){
        if(str==null) {
            return null;
        }
        String[] re = str.split("\n");
        if(re.length <= 1) {
            re = str.split("\r");
        }
        return re;
    }
    // 读取pid
    private String loadPid() throws IOException {
        if(!pidLoc.exists()) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(pidLoc.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }
    // 保存pid
    private void savePid() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("jps", "-l");
        Process process = builder.start();

        // 筛选 app 的pid
        String allOut = getProcessOutput(process.getInputStream());
        String[] lines = spliteStr(allOut);
        String pid = ""; // 如果没有找到，就覆盖空文件
        for(String l : lines) {
            int index = l.indexOf(' ');
            String cusApp = l.substring(index+1);
            if(Objects.equals(cusApp, app)) {
                pid = l.substring(0, index);
            }
        }

        File parent = pidLoc.getParentFile();
        if(parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.write(pidLoc.toPath(), pid.getBytes(StandardCharsets.UTF_8));
    }

    private void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
