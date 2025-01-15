package Network.src;

import java.util.Scanner;

public class Socket {

    private static final int PORT = 8080;
    private static final String HOST = "192.168.88.1";
    public static void main(String[] args) {
        System.out.println("""
                Server: 1
                Client: 2
                """);
        System.out.print("Your choice: ");
        Scanner scanner = new Scanner(System.in);
        int choice = Integer.parseInt(scanner.nextLine());
        switch (choice) {
            // case 1 -> new Server().start(PORT);
            case 1 -> new ServerVirtualThread().start(PORT);
            case 2 -> new Client().connect(HOST, PORT, scanner);
            default -> throw new IllegalStateException("Unknown choice!");   
            }
          
    }
}
