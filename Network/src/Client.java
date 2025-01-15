package Network.src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread receiveThread;

    public void connect(final String host, final int port, final Scanner scanner) {
        try {
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected to chat server");
            System.out.println("Commands:");
            System.out.println("- Send to everyone: Just type your message");
            System.out.println("- Send to specific IP: @IP message");
            System.out.println("- Quit: /quit");

            // Start receive thread
            startReceiveThread();

            // Main thread handles sending messages
            String userInput;
            while (running.get()) {
                System.out.print("You: ");
                userInput = scanner.nextLine();
                if (userInput.equalsIgnoreCase("/quit")) {
                    shutdown();
                    break;
                }
                if (running.get()) {
                    writer.println(userInput);
                }
            }

        } catch (Exception e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            try {
                String serverResponse;
                while (running.get() && (serverResponse = reader.readLine()) != null) {
                    System.out.println(serverResponse);
                    if (running.get()) {
                        System.out.print("You: ");
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.out.println("Lost connection to server");
                    shutdown();
                }
            }
        });
        receiveThread.start();
    }

    private void shutdown() {
        running.set(false);
        // ส่งข้อความว่างเพื่อบอก server ว่าเราจะออก
        if (writer != null) {
            writer.println("");
        }
        cleanup();
    }

    private void cleanup() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
            
            // รอให้ receive thread จบการทำงาน
            if (receiveThread != null) {
                receiveThread.join(1000); // รอ maximum 1 วินาที
            }
            
            System.out.println("Disconnected from server");
            System.exit(0); // ออกจากโปรแกรมทั้งหมด
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}