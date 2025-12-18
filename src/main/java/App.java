import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    // ==========================================
    //            用户配置区域 (已填好)
    // ==========================================
    
    // 1. 哪吒探针配置
    private static final String NEZHA_SERVER_ADDRESS = "nezha.dzb.de5.net:443"; 
    private static final String NEZHA_KEY = "j9TlKKifMtqARy2GEaO6Dnl2hl0bMBiz"; 
    private static final boolean NEZHA_TLS = true; 

    // 2. Cloudflare Argo 隧道配置
    private static final String ARGO_TOKEN = "eyJhIjoiOGU5OTllYmUxNzQ4ZTRjYjA4ZGVjYTcyNDc5NjdmYWMiLCJ0IjoiMDIyYTVmNzYtN2Q5MS00ZWEyLTg2YjAtOWQxODMyM2JhZGUzIiwicyI6IllUWTVNRE5rTXpJdE1qYzJPQzAwWXpJM0xUZzNNRFF0TURNellqWm1NbU01WlRneCJ9"; 
    private static final String ARGO_DOMAIN = "s4webms.dub.de5.net"; 

    // 3. 节点配置
    private static final String UUID = "16fea94d-fa7e-46d1-acc9-9996a7eafe77"; 
    private static final int LOCAL_PORT = 8001; // 注意：这里使用了 8001 端口

    // ==========================================

    private static final String WORK_DIR = System.getProperty("user.dir");
    private static final String XRAY_URL = "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip";
    private static final String NEZHA_URL = "https://github.com/nezhahq/agent/releases/latest/download/nezha-agent_linux_amd64.zip";
    private static final String ARGO_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64";

    public static void main(String[] args) {
        System.out.println(">>> 启动 Java 守护进程 (已配置版)...");
        System.out.println(">>> 监听内部端口: " + LOCAL_PORT);
        printVlessLink();

        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // 使用自定义接口 Task 替代 Runnable 以允许抛出异常
        executor.submit(() -> runProcess("Xray", () -> setupXray()));
        executor.submit(() -> runProcess("Argo", () -> setupArgo()));
        executor.submit(() -> runProcess("Nezha", () -> setupNezha()));
    }

    // 定义一个允许抛出异常的接口
    @FunctionalInterface
    interface Task {
        void execute() throws Exception;
    }

    private static void runProcess(String name, Task setupTask) {
        while (true) {
            try {
                System.out.println("[" + name + "] 正在启动...");
                setupTask.execute(); // 执行任务
            } catch (Exception e) {
                System.err.println("[" + name + "] 发生异常: " + e.getMessage());
                e.printStackTrace();
            }
            System.err.println("[" + name + "] 进程停止，5秒后重启...");
            try { Thread.sleep(5000); } catch (InterruptedException e) {}
        }
    }

    private static void setupXray() throws Exception {
        String binPath = WORK_DIR + "/xray";
        if (!new File(binPath).exists()) {
            downloadAndUnzip(XRAY_URL, "xray.zip", "xray");
            runCmd("chmod +x xray");
        }
        // 注意：这里动态插入了 LOCAL_PORT (8001)
        String config = "{\"log\":{\"loglevel\":\"warning\"},\"inbounds\":[{\"port\":" + LOCAL_PORT + ",\"protocol\":\"vless\",\"settings\":{\"clients\":[{\"id\":\"" + UUID + "\"}]},\"streamSettings\":{\"network\":\"ws\",\"wsSettings\":{\"path\":\"/\"}}}],\"outbounds\":[{\"protocol\":\"freedom\"}]}";
        Files.write(Paths.get("config.json"), config.getBytes());
        new ProcessBuilder("./xray", "-c", "config.json").inheritIO().start().waitFor();
    }

    private static void setupArgo() throws Exception {
        if (ARGO_TOKEN == null || ARGO_TOKEN.length() < 10) return;
        String binPath = WORK_DIR + "/cloudflared";
        if (!new File(binPath).exists()) {
            downloadFile(ARGO_URL, "cloudflared");
            runCmd("chmod +x cloudflared");
        }
        new ProcessBuilder("./cloudflared", "tunnel", "--no-autoupdate", "run", "--token", ARGO_TOKEN).inheritIO().start().waitFor();
    }

    private static void setupNezha() throws Exception {
        if (NEZHA_SERVER_ADDRESS == null || NEZHA_SERVER_ADDRESS.isEmpty()) return;

        String binPath = WORK_DIR + "/nezha-agent";
        if (!new File(binPath).exists()) {
            downloadAndUnzip(NEZHA_URL, "nezha.zip", "nezha-agent");
            runCmd("find . -name 'nezha-agent' -type f -exec mv {} . \\;");
            runCmd("chmod +x nezha-agent");
        }

        ProcessBuilder pb;
        if (NEZHA_TLS) {
            pb = new ProcessBuilder("./nezha-agent", "-s", NEZHA_SERVER_ADDRESS, "-p", NEZHA_KEY, "--tls");
        } else {
            pb = new ProcessBuilder("./nezha-agent", "-s", NEZHA_SERVER_ADDRESS, "-p", NEZHA_KEY);
        }
        pb.inheritIO().start().waitFor();
    }

    private static void downloadFile(String urlStr, String saveName) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, Paths.get(WORK_DIR, saveName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void downloadAndUnzip(String url, String zipName, String finalBin) throws Exception {
        downloadFile(url, zipName);
        runCmd("unzip -o " + zipName);
        new File(zipName).delete();
    }

    private static void runCmd(String cmd) {
        try { new ProcessBuilder("sh", "-c", cmd).start().waitFor(); } catch (Exception e) {}
    }

    private static void printVlessLink() {
        String link = "vless://" + UUID + "@" + ARGO_DOMAIN + ":443?security=tls&encryption=none&type=ws&host=" + ARGO_DOMAIN + "&path=%2F#Java-Node";
        System.out.println("\n========================================================");
        System.out.println(" 节点链接 (复制下方内容到 V2RayN):");
        System.out.println(link);
        System.out.println("========================================================\n");
    }
}
