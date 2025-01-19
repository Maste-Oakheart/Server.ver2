package Network.src;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerVirtualThread {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private ServerGUI serverGUI;
    private final LineNotificationHandler lineNotifier = new LineNotificationHandler();

    private static class ClientHandler {
        private final PrintWriter writer;
        private final String clientId;
        private final String ipAddress;
        private final LocalDateTime connectTime;

        public ClientHandler(PrintWriter writer, String clientId, String ipAddress) {
            this.writer = writer;
            this.clientId = clientId;
            this.ipAddress = ipAddress;
            this.connectTime = LocalDateTime.now();
        }
    }

    public void setServerGUI(ServerGUI serverGUI) {
        this.serverGUI = serverGUI;
    }
    

    protected void logServerEvent(String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);
        // ถ้ามี GUI ให้แสดงผลแค่ที่ GUI
        if (serverGUI != null) {
            serverGUI.appendLog(message);  
        } else {
            // ถ้าไม่มี GUI ค่อยแสดงที่ console
            System.out.println("[" + timestamp + "] " + message);
        }
    }

    public void start(final int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server is running on port: " + port);
            var serverIP = serverSocket.getInetAddress().getHostAddress();
            System.out.println("Server IP: " + serverIP);
            System.out.println("Type '/shutdown' to stop the server");

            executorService = Executors.newVirtualThreadPerTaskExecutor();
            
            // Start console input thread
            startConsoleThread();

            // Main server loop
            while (running.get()) {
                try {
                    var client = serverSocket.accept();
                    if (running.get()) {
                        executorService.submit(() -> handleClient(client));
                    } else {
                        client.close();
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.out.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Server startup error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void startConsoleThread() {
        Thread consoleThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running.get()) {
                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("/shutdown")) {
                    System.out.println("Initiating server shutdown...");
                    shutdown();
                    scanner.close();
                    break;
                }
            }
        });
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    public void shutdown() {
        running.set(false);
        
        try {
            // แจ้งเตือนผ่าน Line ก่อนที่ server จะหยุดทำงาน
            lineNotifier.notifyServerDown("Server shutdown initiated");
            
            // Notify all clients
            broadcastMessage("SERVER", "Server is shutting down...", true);
            
            // Close all client connections
            clients.forEach((clientId, handler) -> {
                handler.writer.close();
            });
            clients.clear();
    
            // Shutdown executor service
            if (executorService != null) {
                executorService.shutdownNow();
            }
    
            // Close server socket
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing server socket: " + e.getMessage());
            }
    
            System.out.println("Server has been shut down.");
            System.exit(0);
        } catch (Exception e) {
            lineNotifier.notifyServerDown("Unexpected error: " + e.getMessage());
            System.out.println("Error during shutdown: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        var clientIP = client.getInetAddress().getHostAddress();
        var clientPort = client.getPort();
        String clientId = clientIP + ":" + clientPort;

        // เช็คจำนวนผู้ใช้ก่อนรับ client ใหม่
        if (!lineNotifier.canAcceptNewClient(clientId, clients.size())) {
            try {
                var output = new PrintWriter(client.getOutputStream(), true);
                output.println("SYSTEM: Server is full. Maximum number of clients reached.");
                output.close();
                client.close();
                logServerEvent("Connection rejected - server full: " + clientId);
                return;
            } catch (IOException e) {
                logServerEvent("Error rejecting client: " + e.getMessage());
                return;
            }
        }

        try (var clientInput = new BufferedReader(new InputStreamReader(client.getInputStream()));
             var output = new PrintWriter(client.getOutputStream(), true)) {

            // Register new client
            var clientHandler = new ClientHandler(output, clientId, clientIP);
            clients.put(clientId, clientHandler);
            lineNotifier.registerNewClient(clientId, clientIP, clients.size());
            logServerEvent("New client connected - IP: " + clientIP + ", Port: " + clientPort);
            logServerEvent("Total clients connected: " + clients.size());
            
            broadcastMessage(clientId, "has joined the chat (IP: " + clientIP + ")", true);
            
            // Send current online users to new client
            output.println("SYSTEM: Current online users:");
            clients.forEach((id, handler) -> {
                if (!id.equals(clientId)) {
                    output.println("SYSTEM: - " + handler.ipAddress + " (ID: " + handler.clientId + ")");
                }
            });

            String input;
            while (running.get() && (input = clientInput.readLine()) != null) {
                if (input.isEmpty()) {
                    logServerEvent("Client " + clientIP + " has quit gracefully");
                    break;
                }
                if (input.startsWith("@")) {
                    handleDirectMessage(clientId, input);
                } else {
                    var message = new Message(ID_GENERATOR.getAndIncrement(), input);
                    logServerEvent("Broadcast from " + clientIP + ": " + input);
                    broadcastMessage(clientId, message.data(), false);
                }
            }

        } catch (Exception e) {  // <-- เปลี่ยนตรงนี้
            if (running.get()) {
                logServerEvent("Client " + clientIP + " disconnected unexpectedly: " + e.getMessage());
                lineNotifier.checkPotentialIntrusion(clientIP); // ตรวจสอบการบุกรุก
            }
        } finally {
            try {
                ClientHandler removedClient = clients.remove(clientId);
                if (removedClient != null && running.get()) {
                    // คำนวณเวลาที่ client อยู่ในระบบ
                    long connectionDuration = java.time.Duration.between(
                        removedClient.connectTime, 
                        LocalDateTime.now()
                    ).toSeconds();
                    
                    logServerEvent("Client " + clientIP + " disconnected after " + 
                                 connectionDuration + " seconds");
                    logServerEvent("Remaining clients: " + clients.size());
                    
                    broadcastMessage(clientId, "has left the chat", true);
                }
                client.close();
            } catch (IOException ex) {
                logServerEvent("Error closing client connection: " + ex.getMessage());
            }
        }
    }

    // ... (handleDirectMessage and broadcastMessage methods remain the same)
    private void handleDirectMessage(String senderId, String input) {
        String[] parts = input.split(" ", 2);
        if (parts.length < 2) {
            clients.get(senderId).writer.println("SYSTEM: Invalid format. Use: @IP message");
            return;
        }

        String targetIP = parts[0].substring(1);
        String message = parts[1];
        
        boolean messageSent = false;
        for (ClientHandler handler : clients.values()) {
            if (handler.ipAddress.equals(targetIP)) {
                handler.writer.println("DM from " + senderId + ": " + message);
                clients.get(senderId).writer.println("DM to " + targetIP + ": " + message);
                messageSent = true;
                System.out.println("Direct message from " + senderId + " to " + targetIP + ": " + message);
                
                // ส่งข้อความนี้ไปยัง GUI
                if (serverGUI != null) {
                    String logMessage = "Direct message from " + senderId + " to " + targetIP + ": " + message;
                    serverGUI.appendLog(logMessage); // ส่งข้อความไปที่ GUI
                }
            }
        }

        if (!messageSent) {
            clients.get(senderId).writer.println("SYSTEM: No user found with IP " + targetIP);
        }
    }

    private void broadcastMessage(String senderId, String message, boolean isSystemMessage) {
        String formattedMessage = isSystemMessage ? 
            "SYSTEM: " + senderId + " " + message :
            senderId + ": " + message;

        clients.forEach((clientId, handler) -> {
            if (isSystemMessage || true) {
                handler.writer.println(formattedMessage);
            }
        });
    }
}