package Network.src;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LineNotificationHandler {
    private static final String LINE_API_URL = "https://notify-api.line.me/api/notify";
    private static final String TOKEN = "lBtd1vht4tg0Qdt0Rz1HRBp2jxsyPOWgUHv9OQvivIT";
    private static final int MAX_CLIENTS = 10;
    private final Set<String> knownClients = new HashSet<>(); // เก็บ clientId แทน IP
    private final AtomicInteger failedLoginAttempts = new AtomicInteger(0);
    private static final int INTRUSION_THRESHOLD = 5;
    private static final long RESET_INTERVAL = 300000;
    private long lastResetTime = System.currentTimeMillis();

    public boolean canAcceptNewClient(String clientId, int currentClientCount) {
        if (currentClientCount >= MAX_CLIENTS) {
            sendLineNotification("⚠️ คำเตือน: ไม่สามารถรับผู้ใช้ใหม่ได้\n" +
                               "ผู้ใช้พยายามเข้าสู่ระบบ: " + clientId + "\n" +
                               "จำนวนผู้ใช้ปัจจุบัน: " + currentClientCount + "\n" +
                               "จำนวนผู้ใช้สูงสุดที่กำหนด: " + MAX_CLIENTS);
            return false;
        }
        return true;
    }

    public void registerNewClient(String clientId, String clientIP, int currentClientCount) {
        if (!knownClients.contains(clientId)) {
            knownClients.add(clientId);
            sendLineNotification("มีผู้ใช้ใหม่เข้าสู่ระบบ\n" +
                               "Client ID: " + clientId + "\n" +
                               "IP Address: " + clientIP + "\n" +
                               "จำนวนผู้ใช้ปัจจุบัน: " + currentClientCount);
        }
    }

    public void checkPotentialIntrusion(String clientIP) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime > RESET_INTERVAL) {
            failedLoginAttempts.set(0);
            lastResetTime = currentTime;
        }

        if (failedLoginAttempts.incrementAndGet() >= INTRUSION_THRESHOLD) {
            sendLineNotification("🚨 การแจ้งเตือนการบุกรุก!\n" +
                               "ตรวจพบความพยายามเข้าถึงที่ผิดปกติ\n" +
                               "IP Address: " + clientIP + "\n" +
                               "จำนวนความพยายาม: " + failedLoginAttempts.get());
            failedLoginAttempts.set(0);
        }
    }

    private void sendLineNotification(String message) {
        try {
            URL url = URI.create(LINE_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + TOKEN);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postData = "message=" + java.net.URLEncoder.encode(message, StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.err.println("Line Notification failed. Response Code: " + responseCode);
            }
        } catch (IOException e) {
            System.err.println("Error sending Line notification: " + e.getMessage());
        }
    }

    public void notifyServerDown(String reason) {
        sendLineNotification("🔴 แจ้งเตือน: Server หยุดทำงาน\n" +
                           "สาเหตุ: " + reason + "\n" +
                           "เวลา: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

}