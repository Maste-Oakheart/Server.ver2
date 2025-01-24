package Network.src;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;

public class ClientGUI extends Application {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private TextArea chatArea;
    private TextField messageField;
    private Button connectButton;
    private TextField ipField;
    private TextField portField;
    private Button sendButton;
    private volatile boolean isConnected = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Chat Client");

        // สร้าง Label สำหรับแสดงคำสั่ง
        Label commandsLabel = new Label(
            "Commands:\n" +
            "- Send to everyone: Just type your message\n" +
            "- Send to specific IP: @IP message"
        );
        commandsLabel.setStyle("-fx-font-family: monospace; -fx-padding: 5;");

        // Create GUI components
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Connection controls
        Label ipLabel = new Label("Server IP:");
        ipField = new TextField("0.0.0.0");
        Label portLabel = new Label("Port:");
        portField = new TextField("8080");
        connectButton = new Button("Connect");

        // Chat area
        chatArea = new TextArea();
        chatArea.setPrefRowCount(20);
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefWidth(500);
        chatArea.setPrefHeight(300);

        // Message input
        messageField = new TextField();
        messageField.setPromptText("Type your message here");
        sendButton = new Button("Send");
        sendButton.setDisable(true);
        messageField.setPrefWidth(400);
        
        // Add components to grid
        grid.add(ipLabel, 0, 0);
        grid.add(ipField, 1, 0);
        grid.add(portLabel, 2, 0);
        grid.add(portField, 3, 0);
        grid.add(connectButton, 4, 0);

        // เพิ่ม commandsLabel ไว้เหนือ chat area
        grid.add(commandsLabel, 0, 1, 5, 1);
        
        VBox chatBox = new VBox(10);
        chatBox.getChildren().addAll(chatArea, messageField, sendButton);
        grid.add(chatBox, 0, 2, 5, 1);

        // กำหนด column constraints เพื่อให้ขยายตามขนาดหน้าต่าง
        ColumnConstraints col1 = new ColumnConstraints();
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);  // ให้คอลัมน์นี้ขยายตามพื้นที่ที่มี
        grid.getColumnConstraints().addAll(col1, col2);

        // Event handlers
        connectButton.setOnAction(e -> toggleConnection());
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        Scene scene = new Scene(grid);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Handle window closing
        primaryStage.setOnCloseRequest(e -> {
            if (isConnected) {
                disconnect();
            }
            Platform.exit();
        });
    }

    private void toggleConnection() {
        if (!isConnected) {
            connect();
        } else {
            disconnect();
        }
    }

    private void connect() {
        try {
            String host = ipField.getText();
            int port = Integer.parseInt(portField.getText());
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start message receiving thread
            new Thread(this::receiveMessages).start();

            isConnected = true;
            connectButton.setText("Disconnect");
            ipField.setDisable(true);
            portField.setDisable(true);
            sendButton.setDisable(false);
            appendToChatArea("Connected to server");
        } catch (Exception e) {
            appendToChatArea("Connection failed: " + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            if (writer != null) {
                writer.println(""); // Send empty message to signal disconnect
                writer.close();
            }
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            appendToChatArea("Error during disconnect: " + e.getMessage());
        } finally {
            isConnected = false;
            connectButton.setText("Connect");
            ipField.setDisable(false);
            portField.setDisable(false);
            sendButton.setDisable(true);
            appendToChatArea("Disconnected from server");
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && isConnected) {
            writer.println(message);
            messageField.clear();
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                final String finalMessage = message;
                Platform.runLater(() -> appendToChatArea(finalMessage));
            }
        } catch (IOException e) {
            if (isConnected) {
                Platform.runLater(() -> appendToChatArea("Lost connection to server"));
                Platform.runLater(this::disconnect);
            }
        }
    }

    private void appendToChatArea(String message) {
        chatArea.appendText(message + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}