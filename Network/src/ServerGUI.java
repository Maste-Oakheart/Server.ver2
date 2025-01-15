package Network.src;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerGUI extends Application {
    private ServerVirtualThread server;
    private TextArea logArea;
    private Button startStopButton;
    private TextField portField;
    private boolean isServerRunning = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Chat Server Control");

        // Create GUI components
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        // Port input
        Label portLabel = new Label("Port:");
        portField = new TextField("8080");
        portField.setPrefWidth(100);

        // Start/Stop button
        startStopButton = new Button("Start Server");
        startStopButton.setPrefWidth(100);

        // Log area
        logArea = new TextArea();
        logArea.setPrefRowCount(20);
        logArea.setEditable(false);
        logArea.setWrapText(true);

        // Add components to grid
        grid.add(portLabel, 0, 0);
        grid.add(portField, 1, 0);
        grid.add(startStopButton, 2, 0);
        grid.add(logArea, 0, 1, 3, 1);

        // Event handlers
        startStopButton.setOnAction(e -> toggleServer());

        Scene scene = new Scene(grid);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Handle window closing
        primaryStage.setOnCloseRequest(e -> {
            if (isServerRunning) {
                stopServer();
            }
            Platform.exit();
        });
    }

    private void toggleServer() {
        if (!isServerRunning) {
            startServer();
        } else {
            stopServer();
        }
    }

    protected void startServer() {
        try {
            int port = Integer.parseInt(portField.getText());
            server = new ServerVirtualThread() {
                @Override
                protected void logServerEvent(String message) {
                    Platform.runLater(() -> appendLog(message));
                }
            };
            
            server.setServerGUI(this);
            // Start server in a separate thread
            new Thread(() -> {
                server.start(port);
            }).start();

            isServerRunning = true;
            startStopButton.setText("Stop Server");
            portField.setDisable(true);
            appendLog("Server started on port " + port);
        } catch (NumberFormatException e) {
            appendLog("Invalid port number");
        }
    }

    private void stopServer() {
        if (server != null) {
            server.shutdown();
            isServerRunning = false;
            startStopButton.setText("Start Server");
            portField.setDisable(false);
            appendLog("Server stopped");
        }
    }

    public void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> logArea.appendText("[" + timestamp + "] " + message + "\n"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}