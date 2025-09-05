package com.example.gamesocket;

// GameClient.java
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.util.*;

public class GameClient extends Application {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Stage primaryStage;

    // UI Components
    private VBox loginPane;
    private VBox mainGamePane;
    private VBox gamePlayPane;

    // Game state
    private String currentUsername;
    private String currentGameId;
    private String opponent;
    private int currentScore = 0;
    private int timeRemaining = 15;
    private Label scoreLabel;
    private Label timerLabel;
    private Label opponentScoreLabel;
    private GridPane grainGrid;
    private Timer gameTimer;

    // Online users
    private ListView<String> userListView;
    private Map<String, UserInfo> onlineUsers;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.onlineUsers = new HashMap<>();

        primaryStage.setTitle("Game Tấm Nhặt Thóc");
        primaryStage.setResizable(false);

        createLoginUI();
        primaryStage.setScene(new Scene(loginPane, 400, 300));
        primaryStage.show();

        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Bắt đầu thread để nhận message từ server
            Thread messageHandler = new Thread(this::handleServerMessages);
            messageHandler.setDaemon(true);
            messageHandler.start();

        } catch (IOException e) {
            showAlert("Lỗi kết nối", "Không thể kết nối đến server!");
            e.printStackTrace();
        }
    }

    private void createLoginUI() {
        loginPane = new VBox(10);
        loginPane.setPadding(new Insets(20));
        loginPane.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("GAME TẤM NHẶT THÓC");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Tên đăng nhập");
        usernameField.setMaxWidth(200);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");
        passwordField.setMaxWidth(200);

        Button loginButton = new Button("Đăng nhập");
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                sendMessage("LOGIN:" + username + "," + password);
            }
        });

        Button registerButton = new Button("Đăng ký");
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                sendMessage("REGISTER:" + username + "," + password);
            }
        });

        HBox buttonBox = new HBox(10, loginButton, registerButton);
        buttonBox.setAlignment(Pos.CENTER);

        loginPane.getChildren().addAll(titleLabel, usernameField, passwordField, buttonBox);
    }

    private void createMainGameUI() {
        mainGamePane = new VBox(10);
        mainGamePane.setPadding(new Insets(10));

        // Header
        Label welcomeLabel = new Label("Chào mừng: " + currentUsername);
        welcomeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // User list
        Label onlineLabel = new Label("Người chơi trực tuyến:");
        userListView = new ListView<>();
        userListView.setPrefHeight(200);
        userListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selectedUser = userListView.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.contains("(BUSY)")) {
                    String username = selectedUser.split(" - ")[0];
                    if (!username.equals(currentUsername)) {
                        sendMessage("INVITE:" + username);
                    }
                }
            }
        });

        // Buttons
        Button leaderboardButton = new Button("Bảng xếp hạng");
        leaderboardButton.setOnAction(e -> {
            sendMessage("GET_LEADERBOARD");
        });

        Button logoutButton = new Button("Đăng xuất");
        logoutButton.setOnAction(e -> {
            disconnect();
            Platform.exit();
        });

        HBox buttonBox = new HBox(10, leaderboardButton, logoutButton);
        buttonBox.setAlignment(Pos.CENTER);

        mainGamePane.getChildren().addAll(welcomeLabel, onlineLabel, userListView, buttonBox);
    }

    private void createGamePlayUI() {
        gamePlayPane = new VBox(10);
        gamePlayPane.setPadding(new Insets(10));

        // Game info
        Label gameInfoLabel = new Label("Đang chơi với: " + opponent);
        gameInfoLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Score and timer
        HBox infoBox = new HBox(20);
        infoBox.setAlignment(Pos.CENTER);

        scoreLabel = new Label("Điểm của bạn: 0");
        scoreLabel.setStyle("-fx-font-size: 12px;");

        opponentScoreLabel = new Label("Điểm đối thủ: 0");
        opponentScoreLabel.setStyle("-fx-font-size: 12px;");

        timerLabel = new Label("Thời gian: 15s");
        timerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: red;");

        infoBox.getChildren().addAll(scoreLabel, opponentScoreLabel, timerLabel);

        // Game grid (5x10 = 50 grains)
        grainGrid = new GridPane();
        grainGrid.setAlignment(Pos.CENTER);
        grainGrid.setHgap(5);
        grainGrid.setVgap(5);

        // Create 50 grain buttons
        for (int i = 0; i < 50; i++) {
            Circle grain = new Circle(15);
            grain.setFill(Color.YELLOW);
            grain.setStroke(Color.BLACK);

            final int grainIndex = i;
            grain.setOnMouseClicked(e -> {
                if (currentGameId != null) {
                    sendMessage("GAME_ACTION:" + grainIndex);
                    grain.setDisable(true);
                }
            });

            grainGrid.add(grain, i % 10, i / 10);
        }

        Button quitButton = new Button("Thoát game");
        quitButton.setOnAction(e -> {
            sendMessage("QUIT_GAME");
            backToMainMenu();
        });

        gamePlayPane.getChildren().addAll(gameInfoLabel, infoBox, grainGrid, quitButton);
    }

    private void handleServerMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                final String msg = message;
                Platform.runLater(() -> processServerMessage(msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                showAlert("Lỗi", "Mất kết nối với server!");
            });
        }
    }

    private void processServerMessage(String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "LOGIN_SUCCESS":
                currentUsername = data;
                createMainGameUI();
                primaryStage.setScene(new Scene(mainGamePane, 500, 400));
                break;

            case "LOGIN_FAILED":
            case "REGISTER_FAILED":
                showAlert("Lỗi", data);
                break;

            case "REGISTER_SUCCESS":
                showAlert("Thành công", "Đăng ký thành công! Vui lòng đăng nhập.");
                break;

            case "ONLINE_USERS":
                updateOnlineUsers(data);
                break;

            case "GAME_INVITATION":
                handleGameInvitation(data);
                break;

            case "INVITATION_REJECTED":
                showAlert("Thông báo", data + " đã từ chối lời mời!");
                break;

            case "GAME_STARTED":
                handleGameStarted(data);
                break;

            case "GRAIN_RESULT":
                handleGrainResult(data);
                break;

            case "OPPONENT_SCORE":
                handleOpponentScore(data);
                break;

            case "GAME_ENDED":
                handleGameEnded(data);
                break;

            case "LEADERBOARD":
                showLeaderboard(data);
                break;
        }
    }

    private void updateOnlineUsers(String data) {
        onlineUsers.clear();
        userListView.getItems().clear();

        if (!data.isEmpty()) {
            String[] users = data.split(";");
            for (String userInfo : users) {
                if (!userInfo.trim().isEmpty()) {
                    String[] parts = userInfo.split(",");
                    if (parts.length >= 3) {
                        String username = parts[0];
                        int totalScore = Integer.parseInt(parts[1]);
                        String status = parts[2];

                        onlineUsers.put(username, new UserInfo(username, totalScore, status));

                        String displayText = username + " - Điểm: " + totalScore;
                        if (status.equals("BUSY")) {
                            displayText += " (BUSY)";
                        }
                        if (!username.equals(currentUsername)) {
                            userListView.getItems().add(displayText);
                        }
                    }
                }
            }
        }
    }

    private void handleGameInvitation(String inviter) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Lời mời chơi game");
        alert.setHeaderText(inviter + " mời bạn chơi game!");
        alert.setContentText("Bạn có muốn chấp nhận?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            sendMessage("ACCEPT_INVITATION:" + inviter);
        } else {
            sendMessage("REJECT_INVITATION:" + inviter);
        }
    }

    private void handleGameStarted(String data) {
        String[] parts = data.split(",");
        currentGameId = parts[0];
        opponent = parts[1];
        timeRemaining = Integer.parseInt(parts[2]);
        currentScore = 0;

        createGamePlayUI();
        primaryStage.setScene(new Scene(gamePlayPane, 600, 500));

        startGameTimer();
    }

    private void startGameTimer() {
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    timeRemaining--;
                    timerLabel.setText("Thời gian: " + timeRemaining + "s");

                    if (timeRemaining <= 0) {
                        gameTimer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    private void handleGrainResult(String data) {
        String[] parts = data.split(",");
        int grainIndex = Integer.parseInt(parts[0]);
        String grainType = parts[1];
        currentScore = Integer.parseInt(parts[2]);

        // Update grain appearance
        Circle grain = (Circle) grainGrid.getChildren().get(grainIndex);
        if (grainType.equals("RICE")) {
            grain.setFill(Color.GREEN);
        } else {
            grain.setFill(Color.BROWN);
        }

        scoreLabel.setText("Điểm của bạn: " + currentScore);
    }

    private void handleOpponentScore(String data) {
        String[] parts = data.split(",");
        int opponentScore = Integer.parseInt(parts[1]);
        opponentScoreLabel.setText("Điểm đối thủ: " + opponentScore);
    }

    private void handleGameEnded(String data) {
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        String[] parts = data.split(",");
        String winner = parts[0];
        int finalScore1 = Integer.parseInt(parts[1]);
        int finalScore2 = Integer.parseInt(parts[2]);

        String resultMessage;
        if (winner.equals(currentUsername)) {
            resultMessage = "Chúc mừng! Bạn đã thắng!\nĐiểm của bạn: " + currentScore;
        } else if (winner.equals("DRAW")) {
            resultMessage = "Hòa!\nĐiểm của bạn: " + currentScore;
        } else if (winner.equals("QUIT")) {
            resultMessage = "Game kết thúc do bạn thoát!";
        } else {
            resultMessage = "Bạn đã thua!\nĐiểm của bạn: " + currentScore;
        }

        showAlert("Kết thúc game", resultMessage);
        backToMainMenu();
    }

    private void showLeaderboard(String data) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Bảng xếp hạng");
        alert.setHeaderText("Top người chơi");

        StringBuilder content = new StringBuilder();
        content.append(String.format("%-15s %-8s %-8s %-8s %-8s\n", "Tên", "Điểm", "Trận", "Thắng", "Tỷ lệ %"));
        content.append("=" .repeat(60)).append("\n");

        if (!data.isEmpty()) {
            String[] players = data.split(";");
            int rank = 1;
            for (String playerInfo : players) {
                if (!playerInfo.trim().isEmpty()) {
                    String[] parts = playerInfo.split(",");
                    if (parts.length >= 5) {
                        String username = parts[0];
                        int totalScore = Integer.parseInt(parts[1]);
                        int gamesPlayed = Integer.parseInt(parts[2]);
                        int gamesWon = Integer.parseInt(parts[3]);
                        String winRate = parts[4];

                        content.append(String.format("%2d. %-12s %-8d %-8d %-8d %-8s\n",
                                rank++, username, totalScore, gamesPlayed, gamesWon, winRate + "%"));
                    }
                }
            }
        }

        alert.setContentText(content.toString());
        alert.getDialogPane().setStyle("-fx-font-family: monospace");
        alert.showAndWait();
    }

    private void backToMainMenu() {
        currentGameId = null;
        opponent = null;
        currentScore = 0;
        primaryStage.setScene(new Scene(mainGamePane, 500, 400));
    }

    private void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void disconnect() {
        try {
            if (gameTimer != null) {
                gameTimer.cancel();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // Helper class for user information
    private static class UserInfo {
        String username;
        int totalScore;
        String status;

        public UserInfo(String username, int totalScore, String status) {
            this.username = username;
            this.totalScore = totalScore;
            this.status = status;
        }
    }
}