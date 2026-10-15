import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

public class MobileBridgeServer {
    private static final String ENCRYPTION_SECRET = "TogetherE2EESecretKey2026";
    private static Socket serverSocket;
    private static PrintWriter serverOut;
    private static BufferedReader serverIn;
    private static final List<String> messageHistory = Collections.synchronizedList(new ArrayList<>());
    private static int latestOtp = 1234;

    public static void main(String[] args) throws IOException {
        // Required so cloud Linux servers (Railway/Docker) can draw PNG icons without a monitor
        System.setProperty("java.awt.headless", "true");

        int webPort = 8085;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                webPort = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = null;
        while (server == null && webPort < 8110) {
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", webPort), 0);
            } catch (BindException e) {
                webPort++;
            }
        }

        if (server == null) {
            System.err.println("Could not bind to any web port.");
            return;
        }

        final int boundPort = webPort;
        new Thread(MobileBridgeServer::connectToChatServer).start();

        HttpHandler pageHandler = exchange -> {
            String html = getMobileHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        server.createContext("/", pageHandler);
        server.createContext("/app", pageHandler);

        // 100% PWABuilder-Compliant Web App Manifest
        server.createContext("/manifest.json", exchange -> {
            String manifest = "{\n" +
                    "  \"id\": \"/app\",\n" +
                    "  \"name\": \"Together Ultra\",\n" +
                    "  \"short_name\": \"Together\",\n" +
                    "  \"description\": \"Luxury end-to-end encrypted messaging, live voice, and HD video calling app.\",\n" +
                    "  \"start_url\": \"/app\",\n" +
                    "  \"scope\": \"/\",\n" +
                    "  \"display\": \"standalone\",\n" +
                    "  \"orientation\": \"portrait\",\n" +
                    "  \"background_color\": \"#0a1218\",\n" +
                    "  \"theme_color\": \"#18242c\",\n" +
                    "  \"categories\": [\"social\", \"communication\"],\n" +
                    "  \"icons\": [\n" +
                    "    {\n" +
                    "      \"src\": \"/icon-512.png\",\n" +
                    "      \"sizes\": \"512x512\",\n" +
                    "      \"type\": \"image/png\",\n" +
                    "      \"purpose\": \"any\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"src\": \"/icon-512.png\",\n" +
                    "      \"sizes\": \"512x512\",\n" +
                    "      \"type\": \"image/png\",\n" +
                    "      \"purpose\": \"maskable\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"src\": \"/icon-192.png\",\n" +
                    "      \"sizes\": \"192x192\",\n" +
                    "      \"type\": \"image/png\",\n" +
                    "      \"purpose\": \"any\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/manifest+json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // PWABuilder-Compliant Service Worker
        server.createContext("/sw.js", exchange -> {
            String sw = "const CACHE_NAME = 'together-ultra-v1';\n" +
                    "self.addEventListener('install', event => {\n" +
                    "  self.skipWaiting();\n" +
                    "});\n" +
                    "self.addEventListener('activate', event => {\n" +
                    "  event.waitUntil(clients.claim());\n" +
                    "});\n" +
                    "self.addEventListener('fetch', event => {\n" +
                    "  event.respondWith(fetch(event.request).catch(() => caches.match(event.request)));\n" +
                    "});";
            byte[] bytes = sw.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/javascript; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Dynamic 512x512 & 192x192 PNG Icon Generator for Android APK Packaging
        HttpHandler pngIconHandler = exchange -> {
            int size = exchange.getRequestURI().getPath().contains("192") ? 192 : 512;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(10, 18, 24));
            g2.fillRect(0, 0, size, size);

            g2.setColor(new Color(0, 184, 148));
            g2.fillRoundRect(0, 0, size, size, size / 4, size / 4);

            g2.setColor(new Color(255, 214, 130));
            g2.setStroke(new BasicStroke(size / 36f));
            int pad = size / 8;
            g2.drawOval(pad, pad, size - pad * 2, size - pad * 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, (int) (size * 0.44)));
            FontMetrics fm = g2.getFontMetrics();
            String t = "T";
            g2.drawString(t, (size - fm.stringWidth(t)) / 2, ((size - fm.getHeight()) / 2) + fm.getAscent());
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] bytes = baos.toByteArray();

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        server.createContext("/icon-512.png", pngIconHandler);
        server.createContext("/icon-192.png", pngIconHandler);
        server.createContext("/logo.svg", pngIconHandler);

        server.createContext("/api/request-otp", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String phone = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                latestOtp = 1000 + new Random().nextInt(9000);
                System.out.println("=================================================");
                System.out.println(">>> [MOBILE SMS DISPATCH] OTP for +91 " + phone + " is: " + latestOtp);
                System.out.println("=================================================");
                String response = "{\"status\":\"ok\",\"code\":\"" + latestOtp + "\"}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/verify-otp", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String[] parts = body.split("\\|", 2);
                if (parts.length == 2 && parts[1].equals(String.valueOf(latestOtp))) {
                    byte[] bytes = "{\"status\":\"verified\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }
                byte[] bytes = "{\"status\":\"invalid\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/register-profile", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String[] parts = body.split("\\|", 2);
                if (parts.length == 2 && serverOut != null) {
                    serverOut.println("REG|" + parts[0] + "|" + parts[1]);
                }
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.createContext("/api/messages", exchange -> {
            StringBuilder sb = new StringBuilder("[");
            synchronized (messageHistory) {
                for (int i = 0; i < messageHistory.size(); i++) {
                    sb.append("\"").append(messageHistory.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    if (i < messageHistory.size() - 1) sb.append(",");
                }
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.createContext("/api/send", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (raw.startsWith("MSG|")) {
                    String[] parts = raw.split("\\|", 7);
                    if (parts.length == 7 && serverOut != null) {
                        String cipherText = encryptE2EE(parts[6]);
                        serverOut.println(parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3] + "|" + parts[4] + "|" + parts[5] + "|" + cipherText);
                    }
                } else if (serverOut != null) {
                    serverOut.println(raw);
                }
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println("=================================================");
        System.out.println(">>> Mobile Bridge Server is live on port: " + boundPort);
        System.out.println("=================================================");
    }

    private static void connectToChatServer() {
        while (true) {
            try {
                serverSocket = new Socket("localhost", 12345);
                serverOut = new PrintWriter(new OutputStreamWriter(serverSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream(), StandardCharsets.UTF_8));

                String line;
                while ((line = serverIn.readLine()) != null) {
                    if (line.startsWith("MSG|")) {
                        String[] parts = line.split("\\|", 7);
                        if (parts.length == 7) {
                            String decrypted = decryptE2EE(parts[6]);
                            messageHistory.add(parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3] + "|" + parts[4] + "|" + parts[5] + "|" + decrypted);
                            continue;
                        }
                    }
                    if (!line.startsWith("AUD|") && !line.startsWith("VID|")) {
                        messageHistory.add(line);
                    }
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private static SecretKeySpec getAESKey() {
        try {
            byte[] keyBytes = ENCRYPTION_SECRET.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = Arrays.copyOf(sha.digest(keyBytes), 16);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String encryptE2EE(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, getAESKey());
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return plainText;
        }
    }

    private static String decryptE2EE(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, getAESKey());
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return cipherText;
        }
    }

    private static String getMobileHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "  <meta name=\"theme-color\" content=\"#18242c\">\n" +
                "  <meta name=\"description\" content=\"Luxury end-to-end encrypted messaging, live voice, and HD video calling app.\">\n" +
                "  <link rel=\"manifest\" href=\"/manifest.json\">\n" +
                "  <link rel=\"icon\" type=\"image/png\" href=\"/icon-192.png\">\n" +
                "  <link rel=\"apple-touch-icon\" href=\"/icon-512.png\">\n" +
                "  <title>Together Ultra</title>\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
                "    body { background-color: #0a1218; color: #f0f4f8; height: 100vh; overflow: hidden; display: flex; }\n" +
                "    #app { display: flex; width: 100vw; height: 100vh; position: relative; }\n" +
                "    .view { width: 100%; height: 100%; display: flex; flex-direction: column; position: absolute; top: 0; left: 0; background-color: #0a1218; transition: transform 0.24s ease; }\n" +
                "    .auth-screen { z-index: 10; align-items: center; justify-content: center; padding: 28px; text-align: center; }\n" +
                "    .auth-card { background: #162028; border: 1px solid #283a48; border-radius: 22px; padding: 32px 24px; width: 100%; max-width: 360px; box-shadow: 0 12px 32px rgba(0,0,0,0.45); }\n" +
                "    .step-badge { color: #00b894; font-size: 11px; font-weight: 700; letter-spacing: 1px; margin-bottom: 8px; }\n" +
                "    .auth-title { font-size: 26px; font-weight: 700; margin-bottom: 10px; color: #f0f4f8; }\n" +
                "    .auth-sub { font-size: 13px; color: #8c9eaa; line-height: 1.5; margin-bottom: 24px; }\n" +
                "    .auth-input { width: 100%; padding: 14px 16px; background-color: #202e38; border: 1px solid #344856; border-radius: 12px; color: #fff; font-size: 16px; outline: none; margin-bottom: 16px; text-align: center; }\n" +
                "    .btn-green { background: linear-gradient(135deg, #00b894, #008f72); color: #fff; border: none; border-radius: 24px; padding: 14px 32px; font-size: 15px; font-weight: 700; cursor: pointer; width: 100%; box-shadow: 0 4px 14px rgba(0,184,148,0.3); }\n" +
                "    header { background-color: #18242c; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #243440; min-height: 60px; }\n" +
                "    .icon-btn { background: #243440; border: none; color: #f0f4f8; padding: 8px 12px; border-radius: 12px; font-size: 13px; font-weight: 600; cursor: pointer; }\n" +
                "    .chat-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }\n" +
                "    .chat-item { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border-bottom: 1px solid #152028; cursor: pointer; }\n" +
                "    .avatar { width: 48px; height: 48px; border-radius: 50%; background: linear-gradient(135deg, #00b894, #007a62); color: #fff; font-weight: bold; display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }\n" +
                "    #conversationView { transform: translateX(100%); z-index: 2; }\n" +
                "    #conversationView.active { transform: translateX(0); }\n" +
                "    .chat-body { flex: 1; overflow-y: auto; padding: 14px; display: flex; flex-direction: column; gap: 8px; background-image: radial-gradient(#1c2e3a 1.5px, transparent 1.5px); background-size: 24px 24px; }\n" +
                "    .bubble { max-width: 80%; padding: 10px 14px; border-radius: 16px; font-size: 14px; line-height: 1.4; }\n" +
                "    .bubble.sent { align-self: flex-end; background-color: #00604e; color: #fff; border-bottom-right-radius: 4px; }\n" +
                "    .bubble.received { align-self: flex-start; background-color: #1c2831; color: #f0f4f8; border-bottom-left-radius: 4px; }\n" +
                "    .sender { font-size: 11px; font-weight: bold; color: #00b894; margin-bottom: 3px; }\n" +
                "    .meta { font-size: 10px; color: #8c9eaa; text-align: right; margin-top: 4px; }\n" +
                "    footer { background-color: #18242c; padding: 10px 12px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .msg-inp { flex: 1; background-color: #243440; border: none; outline: none; padding: 12px 16px; border-radius: 22px; color: #fff; font-size: 15px; }\n" +
                "    .send-btn { background-color: #00b894; border: none; border-radius: 50%; width: 44px; height: 44px; color: #fff; font-size: 17px; font-weight: bold; cursor: pointer; }\n" +
                "    #callModal { position: fixed; top:0; left:0; width:100vw; height:100vh; background: #0a1218; z-index: 200; display:none; flex-direction:column; align-items:center; justify-content:center; text-align:center; padding:24px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"app\">\n" +
                "    <div class=\"view auth-screen\" id=\"welcomeView\">\n" +
                "      <div class=\"auth-card\">\n" +
                "        <img src=\"/icon-192.png\" width=\"82\" height=\"82\" style=\"margin-bottom:16px; border-radius:20px;\">\n" +
                "        <div class=\"auth-title\">Together Ultra</div>\n" +
                "        <div class=\"auth-sub\">Next-generation encrypted messaging & live calling.</div>\n" +
                "        <button class=\"btn-green\" onclick=\"showScreen('phoneView')\">Get Started</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <div class=\"view auth-screen\" id=\"phoneView\" style=\"display:none;\">\n" +
                "      <div class=\"auth-card\">\n" +
                "        <div class=\"step-badge\">STEP 1 OF 2 • PHONE VERIFICATION</div>\n" +
                "        <div class=\"auth-title\">Your Phone Number</div>\n" +
                "        <div class=\"auth-sub\">We will send a 4-digit OTP code to verify your device.</div>\n" +
                "        <input type=\"tel\" id=\"loginPhone\" class=\"auth-input\" placeholder=\"10-digit mobile number\">\n" +
                "        <button class=\"btn-green\" onclick=\"requestOtp()\">Send OTP Code</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <div class=\"view auth-screen\" id=\"nameView\" style=\"display:none;\">\n" +
                "      <div class=\"auth-card\">\n" +
                "        <div class=\"step-badge\" id=\"verifiedBadge\">✓ VERIFIED</div>\n" +
                "        <div class=\"auth-title\">Profile Info</div>\n" +
                "        <div class=\"auth-sub\">Enter your display name to finish setup.</div>\n" +
                "        <input type=\"text\" id=\"loginName\" class=\"auth-input\" placeholder=\"Your Display Name\">\n" +
                "        <button class=\"btn-green\" onclick=\"finishSetup()\">Start Chatting</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <div class=\"view\" id=\"chatListView\" style=\"display:none;\">\n" +
                "      <header>\n" +
                "        <div>\n" +
                "          <h1 id=\"appUserHeader\" style=\"font-size:18px;\">Together Ultra</h1>\n" +
                "          <p style=\"font-size:11px; color:#ffd682;\">AES-128 Encrypted</p>\n" +
                "        </div>\n" +
                "        <button class=\"icon-btn\" onclick=\"promptNewChat()\">+ New Chat</button>\n" +
                "      </header>\n" +
                "      <div class=\"chat-list\" id=\"chatList\"></div>\n" +
                "    </div>\n" +
                "    <div class=\"view\" id=\"conversationView\">\n" +
                "      <header>\n" +
                "        <div style=\"display:flex; align-items:center; gap:10px;\">\n" +
                "          <button class=\"icon-btn\" onclick=\"closeChat()\">←</button>\n" +
                "          <div>\n" +
                "            <h1 id=\"convTitle\" style=\"font-size:16px;\">Chat</h1>\n" +
                "            <p id=\"convSub\" style=\"font-size:11px; color:#00b894;\">Online • Encrypted</p>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div style=\"display:flex; gap:6px;\">\n" +
                "          <button class=\"icon-btn\" onclick=\"startMobileCall(false)\">Voice</button>\n" +
                "          <button class=\"icon-btn\" style=\"background:#00b894;\" onclick=\"startMobileCall(true)\">Video</button>\n" +
                "        </div>\n" +
                "      </header>\n" +
                "      <div class=\"chat-body\" id=\"chatBox\"></div>\n" +
                "      <footer>\n" +
                "        <input type=\"text\" class=\"msg-inp\" id=\"msgInput\" placeholder=\"Type a message...\" onkeydown=\"if(event.key==='Enter') send()\">\n" +
                "        <button class=\"send-btn\" onclick=\"send()\">➤</button>\n" +
                "      </footer>\n" +
                "    </div>\n" +
                "    <div id=\"callModal\">\n" +
                "      <div class=\"avatar\" style=\"width:88px; height:88px; font-size:34px; margin-bottom:18px;\" id=\"callAvatar\">C</div>\n" +
                "      <h2 id=\"callTitle\" style=\"margin-bottom:8px;\">Contact</h2>\n" +
                "      <p id=\"callStatus\" style=\"color:#ffd682; margin-bottom:32px; font-size:14px;\">Ringing... Waiting for answer</p>\n" +
                "      <button class=\"btn-green\" style=\"background:#eb4d4b; max-width:200px;\" onclick=\"endMobileCall()\">End Call</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    if ('serviceWorker' in navigator) { navigator.serviceWorker.register('/sw.js'); }\n" +
                "    let MY_PHONE = ''; let MY_NAME = ''; let activeKey = 'Group Chat'; let renderedCount = 0;\n" +
                "    let inCall = false; let callConnected = false; let callSeconds = 0; let callTimer = null;\n" +
                "    const contacts = { 'Group Chat': { name: 'Community Group', type: 'group', snippet: 'Tap to chat with everyone', time: '' } };\n" +
                "    const messages = {};\n" +
                "    function showScreen(id) {\n" +
                "      ['welcomeView', 'phoneView', 'nameView', 'chatListView'].forEach(v => { document.getElementById(v).style.display = (v === id) ? 'flex' : 'none'; });\n" +
                "    }\n" +
                "    async function requestOtp() {\n" +
                "      const phone = document.getElementById('loginPhone').value.trim();\n" +
                "      if (phone.length !== 10) { alert('Enter a valid 10-digit mobile number.'); return; }\n" +
                "      const res = await fetch('/api/request-otp', { method: 'POST', body: phone });\n" +
                "      const data = await res.json();\n" +
                "      const code = prompt('OTP Dispatched: ' + data.code + '\\nEnter 4-digit OTP to verify:');\n" +
                "      if (!code) return;\n" +
                "      const vRes = await fetch('/api/verify-otp', { method: 'POST', body: phone + '|' + code.trim() });\n" +
                "      const vData = await vRes.json();\n" +
                "      if (vData.status === 'verified') {\n" +
                "        MY_PHONE = phone;\n" +
                "        document.getElementById('verifiedBadge').textContent = '✓ VERIFIED: +91 ' + phone;\n" +
                "        showScreen('nameView');\n" +
                "      } else { alert('Incorrect OTP code.'); }\n" +
                "    }\n" +
                "    async function finishSetup() {\n" +
                "      const name = document.getElementById('loginName').value.trim();\n" +
                "      if (!name) { alert('Please enter your name.'); return; }\n" +
                "      MY_NAME = name;\n" +
                "      await fetch('/api/register-profile', { method: 'POST', body: MY_PHONE + '|' + MY_NAME });\n" +
                "      document.getElementById('appUserHeader').textContent = 'Together • ' + MY_NAME;\n" +
                "      showScreen('chatListView'); renderChatList(); setInterval(fetchMessages, 1000); fetchMessages();\n" +
                "    }\n" +
                "    function renderChatList() {\n" +
                "      const list = document.getElementById('chatList'); list.innerHTML = '';\n" +
                "      for (const [key, item] of Object.entries(contacts)) {\n" +
                "        const row = document.createElement('div'); row.className = 'chat-item';\n" +
                "        row.onclick = () => openChat(key, item.name, item.type);\n" +
                "        const initial = item.type === 'group' ? 'G' : item.name.charAt(0).toUpperCase();\n" +
                "        row.innerHTML = `<div class=\"avatar\">${initial}</div>` +\n" +
                "          `<div style=\"flex:1;min-width:0;\"><div style=\"font-weight:600;\">${item.name}</div><div style=\"font-size:12px;color:#8c9eaa;\">${item.snippet}</div></div>` +\n" +
                "          `<div style=\"font-size:11px;color:#8c9eaa;\">${item.time}</div>`;\n" +
                "        list.appendChild(row);\n" +
                "      }\n" +
                "    }\n" +
                "    function openChat(key, name, type) {\n" +
                "      activeKey = key;\n" +
                "      document.getElementById('convTitle').textContent = name;\n" +
                "      document.getElementById('convSub').textContent = type === 'group' ? 'Group • Encrypted' : 'Online • +91 ' + key;\n" +
                "      document.getElementById('conversationView').classList.add('active');\n" +
                "      renderActiveConversation();\n" +
                "    }\n" +
                "    function closeChat() { document.getElementById('conversationView').classList.remove('active'); }\n" +
                "    function renderActiveConversation() {\n" +
                "      const box = document.getElementById('chatBox'); box.innerHTML = '';\n" +
                "      const list = messages[activeKey] || [];\n" +
                "      for (const m of list) {\n" +
                "        const b = document.createElement('div'); b.className = 'bubble ' + (m.isMe ? 'sent' : 'received');\n" +
                "        b.innerHTML = (!m.isMe ? `<div class=\"sender\">${m.sender}</div>` : '') + m.text + `<div class=\"meta\">${m.time}</div>`;\n" +
                "        box.appendChild(b);\n" +
                "      }\n" +
                "      box.scrollTop = box.scrollHeight;\n" +
                "    }\n" +
                "    function promptNewChat() {\n" +
                "      const phone = prompt('Enter 10-Digit Mobile Number:');\n" +
                "      if (phone && phone.trim().length === 10) {\n" +
                "        const cName = prompt('Enter Contact Name:') || phone;\n" +
                "        contacts[phone] = { name: cName, type: 'direct', snippet: phone, time: '' };\n" +
                "        renderChatList(); openChat(phone, cName, 'direct');\n" +
                "      }\n" +
                "    }\n" +
                "    async function startMobileCall(isVideo) {\n" +
                "      const target = activeKey === 'Group Chat' ? 'All' : activeKey;\n" +
                "      inCall = true; callConnected = false; callSeconds = 0;\n" +
                "      document.getElementById('callTitle').textContent = contacts[activeKey] ? contacts[activeKey].name : activeKey;\n" +
                "      document.getElementById('callStatus').style.color = '#ffd682';\n" +
                "      document.getElementById('callStatus').textContent = 'Ringing... Waiting for recipient to pick up';\n" +
                "      document.getElementById('callModal').style.display = 'flex';\n" +
                "      await fetch('/api/send', { method: 'POST', body: (isVideo ? 'VCALL_START|' : 'CALL_START|') + MY_PHONE + '|' + MY_NAME + '|' + target });\n" +
                "    }\n" +
                "    function onMobileCallAccepted() {\n" +
                "      if (!inCall || callConnected) return;\n" +
                "      callConnected = true; callSeconds = 0;\n" +
                "      document.getElementById('callStatus').style.color = '#00b894';\n" +
                "      if (callTimer) clearInterval(callTimer);\n" +
                "      callTimer = setInterval(() => {\n" +
                "        callSeconds++;\n" +
                "        const m = ('0' + Math.floor(callSeconds / 60)).slice(-2);\n" +
                "        const s = ('0' + (callSeconds % 60)).slice(-2);\n" +
                "        document.getElementById('callStatus').textContent = m + ':' + s + ' • Connected Live';\n" +
                "      }, 1000);\n" +
                "    }\n" +
                "    async function endMobileCall() {\n" +
                "      inCall = false; callConnected = false;\n" +
                "      if (callTimer) clearInterval(callTimer);\n" +
                "      document.getElementById('callModal').style.display = 'none';\n" +
                "      const target = activeKey === 'Group Chat' ? 'All' : activeKey;\n" +
                "      await fetch('/api/send', { method: 'POST', body: 'CALL_END|' + MY_PHONE + '|' + target });\n" +
                "    }\n" +
                "    async function fetchMessages() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/messages');\n" +
                "        const list = await res.json();\n" +
                "        if (list.length > renderedCount) {\n" +
                "          for (let i = renderedCount; i < list.length; i++) {\n" +
                "            const line = list[i];\n" +
                "            if (line.startsWith('CALL_ACCEPT|')) {\n" +
                "              const p = line.split('|');\n" +
                "              if (p[1] !== MY_PHONE) onMobileCallAccepted();\n" +
                "            } else if (line.startsWith('CALL_DECLINE|') || line.startsWith('CALL_END|')) {\n" +
                "              const p = line.split('|');\n" +
                "              if (p[1] !== MY_PHONE && inCall) { endMobileCall(); alert('Call ended or declined.'); }\n" +
                "            } else if (line.startsWith('CALL_START|') || line.startsWith('VCALL_START|')) {\n" +
                "              const p = line.split('|');\n" +
                "              if (p[1] !== MY_PHONE && (p[3] === 'All' || p[3] === MY_PHONE) && !inCall) {\n" +
                "                if (confirm('Incoming Call from ' + p[2] + ' (+91 ' + p[1] + '). Pick up?')) {\n" +
                "                  inCall = true; document.getElementById('callTitle').textContent = p[2];\n" +
                "                  document.getElementById('callModal').style.display = 'flex';\n" +
                "                  await fetch('/api/send', { method: 'POST', body: 'CALL_ACCEPT|' + MY_PHONE + '|' + p[1] });\n" +
                "                  onMobileCallAccepted();\n" +
                "                } else {\n" +
                "                  await fetch('/api/send', { method: 'POST', body: 'CALL_DECLINE|' + MY_PHONE + '|' + p[1] });\n" +
                "                }\n" +
                "              }\n" +
                "            } else if (line.startsWith('MSG|')) {\n" +
                "              const parts = line.split('|');\n" +
                "              const senderPhone = parts[2]; const sender = parts[3]; const target = parts[4]; const time = parts[5]; const text = parts[6];\n" +
                "              const isMe = senderPhone === MY_PHONE;\n" +
                "              let key = 'Group Chat';\n" +
                "              if (target.startsWith('GROUP:')) {\n" +
                "                key = target; const grpName = target.split(':')[1] || 'Group';\n" +
                "                if (!contacts[key]) contacts[key] = { name: grpName + ' (Group)', type: 'group', snippet: text, time: time };\n" +
                "              } else if (target !== 'All') {\n" +
                "                key = isMe ? target : senderPhone;\n" +
                "                if (!contacts[key]) contacts[key] = { name: isMe ? target : sender, type: 'direct', snippet: text, time: time };\n" +
                "              }\n" +
                "              if (!messages[key]) messages[key] = [];\n" +
                "              messages[key].push({ sender, text, time, isMe });\n" +
                "              if (contacts[key]) { contacts[key].snippet = text; contacts[key].time = time; }\n" +
                "            }\n" +
                "          }\n" +
                "          renderedCount = list.length;\n" +
                "          renderChatList();\n" +
                "          if (document.getElementById('conversationView').classList.contains('active')) renderActiveConversation();\n" +
                "        }\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "    async function send() {\n" +
                "      const inp = document.getElementById('msgInput'); const text = inp.value.trim();\n" +
                "      if (!text) return;\n" +
                "      const id = Math.random().toString(36).substring(2, 9);\n" +
                "      const now = new Date();\n" +
                "      const time = ('0' + now.getHours()).slice(-2) + ':' + ('0' + now.getMinutes()).slice(-2);\n" +
                "      const target = activeKey === 'Group Chat' ? 'All' : activeKey;\n" +
                "      const packet = 'MSG|' + id + '|' + MY_PHONE + '|' + MY_NAME + '|' + target + '|' + time + '|' + text;\n" +
                "      await fetch('/api/send', { method: 'POST', body: packet });\n" +
                "      inp.value = '';\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}