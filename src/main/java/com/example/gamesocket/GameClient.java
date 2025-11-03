package com.example.gamesocket;

// GameClient.java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class GameClient extends Application {
    private static final int SCENE_WIDTH = 1000;
    private static final int SCENE_HEIGHT = 600;
    // private static final String SERVER_HOST = "localhost"; // XÓA dòng này
    private static final int SERVER_PORT = 8888;

    private String serverHost; // Thêm biến động để lưu địa chỉ server

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Stage primaryStage;

    // UI Components
    private VBox loginPane;
    private VBox registerPane;  // Thêm pane đăng ký riêng
    private VBox mainGamePane;
    private VBox gamePlayPane;
    private VBox gameLobbyPane;
    private VBox leaderboardPane; // Thêm VBox cho leaderboard

    // Game state
    private String currentUsername;
    private String currentGameId;
    private String currentLobbyId;
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
    private Map<String, ChatWindow> openChatWindows = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.onlineUsers = new HashMap<>();

        primaryStage.setTitle("Game Tấm Nhặt Thóc");
        primaryStage.setResizable(false);

        createLoginUI();
        Scene loginScene = new Scene(loginPane, SCENE_WIDTH, SCENE_HEIGHT); // Tăng height lên 400 để chứa thêm trường server

        // ÁP DỤNG CSS VÀO SCENE
        loginScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());

        primaryStage.setScene(loginScene);
        primaryStage.show();

        // KHÔNG TỰ ĐỘNG CONNECT NỮA - đợi user nhập và bấm đăng nhập
        // connectToServer();
    }

    private void connectToServer(String host) {
        try {
            this.serverHost = host;
            socket = new Socket(serverHost, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Bắt đầu thread để nhận message từ server
            Thread messageHandler = new Thread(this::handleServerMessages);
            messageHandler.setDaemon(true);
            messageHandler.start();

        } catch (IOException e) {
            Platform.runLater(() -> {
                showErrorAlert("Lỗi kết nối", "Không thể kết nối đến server tại " + host + ":" + SERVER_PORT +
                         "\n\nVui lòng kiểm tra:\n- Địa chỉ IP có đúng không?\n- Server đã chạy chưa?\n- Firewall có chặn không?");
            });
            e.printStackTrace();
        }
    }

    private void createLoginUI() {
        loginPane = new VBox(10);
        loginPane.setPadding(new Insets(20));
        loginPane.setAlignment(Pos.CENTER);
        loginPane.getStyleClass().add("main-pane");

        Label titleLabel = new Label("GAME TẤM NHẶT THÓC");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        titleLabel.getStyleClass().add("title-label");

        Label subtitleLabel = new Label("Đăng nhập để bắt đầu chơi");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        // THÊM TRƯỜNG NHẬP ĐỊA CHỈ SERVER
        Label serverLabel = new Label("Địa chỉ Server:");
        serverLabel.setStyle("-fx-font-size: 12px;");

        TextField serverField = new TextField("localhost");
        serverField.setPromptText("Nhập IP server (ví dụ: 192.168.1.100)");
        serverField.setMaxWidth(300);
        serverField.setStyle("-fx-font-size: 12px;");

        Label serverHintLabel = new Label("💡 Nhập 'localhost' nếu chơi 1 mình, hoặc IP của bạn bè");
        serverHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999; -fx-font-style: italic;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Tên đăng nhập");
        usernameField.setMaxWidth(200);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");
        passwordField.setMaxWidth(200);

        Button loginButton = new Button("🔑 Đăng nhập");
        loginButton.setOnAction(e -> {
            String server = serverField.getText().trim();
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();

            if (server.isEmpty()) {
                showErrorAlert("Lỗi", "Vui lòng nhập địa chỉ server!");
                return;
            }

            if (!username.isEmpty() && !password.isEmpty()) {
                // Kết nối đến server trước
                connectToServer(server);
                // Đợi một chút để kết nối
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        if (socket != null && socket.isConnected()) {
                            sendMessage("LOGIN:" + username + "," + password);
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }).start();
            } else {
                showErrorAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            }
        });

        Button registerButton = new Button("📝 Tạo tài khoản mới");
        registerButton.setOnAction(e -> showRegisterForm(serverField.getText().trim()));

        HBox buttonBox = new HBox(10, loginButton, registerButton);
        buttonBox.setAlignment(Pos.CENTER);

        loginPane.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            serverLabel,
            serverField,
            serverHintLabel,
            usernameField,
            passwordField,
            buttonBox
        );
    }

    private void createRegisterUI() {
        registerPane = new VBox(15);
        registerPane.setPadding(new Insets(20));
        registerPane.setAlignment(Pos.CENTER);
        registerPane.getStyleClass().add("main-pane");

        Label titleLabel = new Label("ĐĂNG KÝ TÀI KHOẢN");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        titleLabel.getStyleClass().add("title-label");

        Label subtitleLabel = new Label("Tạo tài khoản mới để tham gia trò chơi");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        // TRƯỜNG SERVER - QUAN TRỌNG ĐỂ KẾT NỐI VÀO SERVER TỪ MÁY KHÁC
        VBox serverBox = new VBox(5);
        serverBox.setAlignment(Pos.CENTER);
        Label serverLabel = new Label("Địa chỉ Server:");
        serverLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        TextField serverFieldReg = new TextField("localhost");
        serverFieldReg.setPromptText("Nhập IP server (ví dụ: 192.168.1.100)");
        serverFieldReg.setPrefWidth(300);
        serverFieldReg.setMaxWidth(300);
        serverFieldReg.getStyleClass().add("register-input");

        Label serverHintLabel = new Label("💡 Nếu server ở máy khác, nhập địa chỉ IP của máy đó");
        serverHintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999; -fx-font-style: italic;");
        serverHintLabel.setWrapText(true);
        serverHintLabel.setMaxWidth(300);

        serverBox.getChildren().addAll(serverLabel, serverFieldReg, serverHintLabel);

        // Username field với validation
        VBox usernameBox = new VBox(5);
        usernameBox.setAlignment(Pos.CENTER);
        Label usernameLabel = new Label("Tên đăng nhập:");
        usernameLabel.setStyle("-fx-font-weight: bold;");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Nhập tên đăng nhập (3-20 ký tự)");
        usernameField.setPrefWidth(300);
        usernameField.setMaxWidth(300);
        usernameField.getStyleClass().add("register-input");
        usernameBox.getChildren().addAll(usernameLabel, usernameField);

        // Password field với validation
        VBox passwordBox = new VBox(5);
        passwordBox.setAlignment(Pos.CENTER);
        Label passwordLabel = new Label("Mật khẩu:");
        passwordLabel.setStyle("-fx-font-weight: bold;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Nhập mật khẩu (tối thiểu 6 ký tự)");
        passwordField.setPrefWidth(300);
        passwordField.setMaxWidth(300);
        passwordField.getStyleClass().add("register-input");
        passwordBox.getChildren().addAll(passwordLabel, passwordField);

        // Confirm password field
        VBox confirmPasswordBox = new VBox(5);
        confirmPasswordBox.setAlignment(Pos.CENTER);
        Label confirmPasswordLabel = new Label("Xác nhận mật khẩu:");
        confirmPasswordLabel.setStyle("-fx-font-weight: bold;");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu");
        confirmPasswordField.setPrefWidth(300);
        confirmPasswordField.setMaxWidth(300);
        confirmPasswordField.getStyleClass().add("register-input");
        confirmPasswordBox.getChildren().addAll(confirmPasswordLabel, confirmPasswordField);

        // Status label để hiển thị quá trình kết nối
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #007bff;");
        statusLabel.setVisible(false);

        // Buttons
        Button registerButton = new Button("✨ Đăng ký");
        registerButton.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #218838); -fx-text-fill: white; -fx-font-weight: bold;");
        registerButton.setOnAction(e -> {
            String server = serverFieldReg.getText().trim();
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            String confirmPassword = confirmPasswordField.getText().trim();

            // Validation đầy đủ
            if (server.isEmpty()) {
                showErrorAlert("Lỗi", "Vui lòng nhập địa chỉ server!\n\nVí dụ:\n- localhost (nếu server trên máy bạn)\n- 192.168.1.100 (nếu server ở máy khác trong cùng mạng)");
                return;
            }

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                showErrorAlert("Lỗi", "Vui lòng điền đầy đủ thông tin!");
                return;
            }

            if (username.length() < 3 || username.length() > 20) {
                showErrorAlert("Lỗi", "Tên đăng nhập phải từ 3-20 ký tự!");
                return;
            }

            if (password.length() < 6) {
                showErrorAlert("Lỗi", "Mật khẩu phải có ít nhất 6 ký tự!");
                return;
            }

            if (!password.equals(confirmPassword)) {
                showErrorAlert("Lỗi", "Mật khẩu xác nhận không khớp!");
                return;
            }

            // Disable button và hiển thị trạng thái
            registerButton.setDisable(true);
            statusLabel.setText("⏳ Đang kết nối đến server " + server + "...");
            statusLabel.setVisible(true);

            // Kết nối và đăng ký trong thread riêng
            new Thread(() -> {
                try {
                    // Đóng kết nối cũ nếu có
                    if (socket != null && !socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }

                    // Kết nối đến server
                    connectToServer(server);

                    // Đợi kết nối được thiết lập
                    Thread.sleep(800);

                    if (socket != null && socket.isConnected()) {
                        Platform.runLater(() -> {
                            statusLabel.setText("✅ Đã kết nối! Đang gửi thông tin đăng ký...");
                        });

                        // Gửi yêu cầu đăng ký
                        sendMessage("REGISTER:" + username + "," + password);

                    } else {
                        Platform.runLater(() -> {
                            statusLabel.setVisible(false);
                            registerButton.setDisable(false);
                            showErrorAlert("Lỗi kết nối",
                                    "Không thể kết nối đến server tại " + server + ":" + SERVER_PORT +
                                            "\n\nVui lòng kiểm tra:\n" +
                                            "1. Server đã chạy chưa?\n" +
                                            "2. Địa chỉ IP có đúng không?\n" +
                                            "3. Cùng mạng WiFi/LAN không?\n" +
                                            "4. Firewall có chặn port " + SERVER_PORT + " không?");
                        });
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }).start();
        });

        Button backButton = new Button("🔙 Quay lại đăng nhập");
        backButton.setOnAction(e -> {
            // Đóng kết nối nếu có
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            showLoginForm();
        });

        HBox buttonBox = new HBox(15, registerButton, backButton);
        buttonBox.setAlignment(Pos.CENTER);

        // Thêm hướng dẫn chi tiết hơn
        Label instructionLabel = new Label(
                "📋 Hướng dẫn đăng ký:\n" +
                        "1. Nhập địa chỉ IP của server (hoặc 'localhost' nếu server trên máy bạn)\n" +
                        "2. Điền thông tin tài khoản\n" +
                        "3. Sau khi đăng ký thành công, hãy đăng nhập để chơi!"
        );
        instructionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-text-alignment: center;");
        instructionLabel.setWrapText(true);
        instructionLabel.setMaxWidth(350);

        registerPane.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                serverBox,
                usernameBox,
                passwordBox,
                confirmPasswordBox,
                statusLabel,
                buttonBox,
                instructionLabel
        );
    }

    private void showRegisterForm(String serverHost) {
        // Tạo lại registerPane
        createRegisterUI();

        // Nếu có serverHost được truyền vào, điền sẵn vào trường server
        if (serverHost != null && !serverHost.isEmpty()) {
            // Tìm TextField server trong registerPane và set giá trị
            registerPane.getChildren().stream()
                    .filter(node -> node instanceof TextField)
                    .map(node -> (TextField) node)
                    .filter(tf -> tf.getPromptText().contains("IP server"))
                    .findFirst()
                    .ifPresent(tf -> tf.setText(serverHost));
        }

        Scene registerScene = new Scene(registerPane, SCENE_WIDTH, SCENE_HEIGHT);
        registerScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
        primaryStage.setScene(registerScene);
    }

    private void showLoginForm() {
        // Tạo lại loginPane để tránh lỗi JavaFX Node đã được sử dụng
        createLoginUI();
        Scene loginScene = new Scene(loginPane, SCENE_WIDTH, SCENE_HEIGHT);
        loginScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
        primaryStage.setScene(loginScene);
    }

    private void createMainGameUI() {
        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(10));

        // TOP: Lời chào mừng
        Label welcomeLabel = new Label("Chào mừng: " + currentUsername);
        welcomeLabel.getStyleClass().add("welcome-label");
        BorderPane.setAlignment(welcomeLabel, Pos.CENTER);
        borderPane.setTop(welcomeLabel);

        // CENTER: Danh sách người chơi
        VBox userListBox = new VBox(5);
        Label onlineLabel = new Label("Người chơi trực tuyến (click chuột phải để mời):");
        userListView = new ListView<>();

        // =================== BỔ SUNG PHẦN CODE BỊ THIẾU ===================
        // BƯỚC 1: Tạo ContextMenu và MenuItem
        ContextMenu userContextMenu = new ContextMenu();
        MenuItem inviteMenuItem = new MenuItem("Mời chơi");
        MenuItem chatMenuItem = new MenuItem("Nhắn tin");

        userContextMenu.getItems().addAll(inviteMenuItem, chatMenuItem);

        // BƯỚC 2: Sử dụng setCellFactory để tùy chỉnh từng hàng và gán ContextMenu
        userListView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>();
            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(userContextMenu);

                    // Hành động mời chơi
                    inviteMenuItem.setOnAction(event -> {
                        String selectedItem = cell.getItem();
                        if (selectedItem != null) {
                            String targetUsername = selectedItem.split(" - ")[0];
                            if (selectedItem.contains("(BUSY)")) {
                                showErrorAlert("Không thể mời", targetUsername + " đang ở trong trận!");
                            } else if (targetUsername.equals(currentUsername)) {
                                showErrorAlert("Không thể mời", "Bạn không thể tự mời chính mình!");
                            } else {
                                sendMessage("INVITE:" + targetUsername);
                            }
                        }
                    });

                    // Hành động nhắn tin
                    chatMenuItem.setOnAction(event -> {
                        String selectedItem = cell.getItem();
                        if (selectedItem != null) {
                            String targetUsername = selectedItem.split(" - ")[0];
                            if (!targetUsername.equals(currentUsername)) {
                                openPrivateChat(targetUsername);
                            }
                        }
                    });
                }
            });

            cell.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    cell.setText(newItem);
                } else {
                    cell.setText(null);
                }
            });

            return cell;
        });
        // =================== KẾT THÚC PHẦN BỔ SUNG ===================

        userListBox.getChildren().addAll(onlineLabel, userListView);
        borderPane.setCenter(userListBox);
        BorderPane.setMargin(userListBox, new Insets(10, 5, 0, 0));


        // RIGHT: Khu vực chat (tạm thời ẩn đi)
        // VBox chatPane = createChatPane();
        // borderPane.setRight(chatPane);
        // BorderPane.setMargin(chatPane, new Insets(10, 0, 0, 5));

        // BOTTOM: Các nút chức năng
        Button leaderboardButton = new Button("Bảng xếp hạng");
        leaderboardButton.setOnAction(e -> sendMessage("GET_LEADERBOARD"));
        Button logoutButton = new Button("Đăng xuất");
        logoutButton.setOnAction(e -> {
            // Reset thông tin người dùng
            currentUsername = null;
            currentGameId = null;
            opponent = null;
            currentScore = 0;

            // Đóng tất cả chat windows
            for (ChatWindow chatWindow : openChatWindows.values()) {
                chatWindow.close();
            }
            openChatWindows.clear();

            // Quay về giao diện đăng nhập thay vì thoát ứng dụng
            showLoginForm();
        });
        HBox buttonBox = new HBox(10, leaderboardButton, logoutButton);
        buttonBox.setAlignment(Pos.CENTER);
        borderPane.setBottom(buttonBox);
        BorderPane.setMargin(buttonBox, new Insets(10, 0, 0, 0));

        // Gán mainGamePane là borderPane
        mainGamePane = new VBox(borderPane);
        mainGamePane.getStyleClass().add("root");
    }

    private void createLobbyUI(String lobbyId, String host, String[] players) {
        gameLobbyPane = new VBox(15);
        gameLobbyPane.setPadding(new Insets(20));
        gameLobbyPane.setAlignment(Pos.CENTER);
        gameLobbyPane.getStyleClass().add("main-pane");

        Label titleLabel = new Label("PHÒNG CHỜ");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label lobbyIdLabel = new Label("Mã phòng: " + lobbyId);
        lobbyIdLabel.setStyle("-fx-font-size: 14px;");

        ListView<String> playerListView = new ListView<>();
        for (String player : players) {
            playerListView.getItems().add(player);
        }

        Button startGameButton = new Button("Bắt đầu chơi");
        startGameButton.setVisible(currentUsername.equals(host));
        startGameButton.setOnAction(e -> {
            sendMessage("START_GAME:" + lobbyId);
        });

        gameLobbyPane.getChildren().addAll(titleLabel, lobbyIdLabel, playerListView, startGameButton);
    }

    private void openPrivateChat(String recipient) {
        if (openChatWindows.containsKey(recipient)) {
            openChatWindows.get(recipient).toFront();
            return;
        }

        Consumer<String> messageSender = message -> {
            sendMessage("PRIVATE_MESSAGE:" + recipient + ":" + message);
        };

        ChatWindow chatWindow = new ChatWindow(currentUsername, recipient, messageSender);
        openChatWindows.put(recipient, chatWindow);
        chatWindow.setOnHidden(e -> openChatWindows.remove(recipient));
        chatWindow.show();
    }

    private void createGamePlayUI() {
        gamePlayPane = new VBox(15);
        gamePlayPane.setPadding(new Insets(15));
        gamePlayPane.setAlignment(Pos.CENTER);
        gamePlayPane.getStyleClass().add("root");

        // Game info với styling đẹp hơn
        Label gameInfoLabel = new Label("🌾 Đang chơi với: " + opponent + " 🌾");
        gameInfoLabel.getStyleClass().add("game-info-label");

        // Score and timer với styling riêng biệt
        HBox infoBox = new HBox(30);
        infoBox.setAlignment(Pos.CENTER);

        scoreLabel = new Label("🌾 Điểm của bạn: 0");
        scoreLabel.getStyleClass().add("score-label");

        opponentScoreLabel = new Label("⚔️ Điểm đối thủ: 0");
        opponentScoreLabel.getStyleClass().add("opponent-score-label");

        timerLabel = new Label("⏰ Thời gian: 15s");
        timerLabel.getStyleClass().add("timer-label");

        infoBox.getChildren().addAll(scoreLabel, opponentScoreLabel, timerLabel);

        // Hướng dẫn cho người chơi
        Label instructionLabel = new Label("💡 Hướng dẫn: Click vào hạt thóc (màu trắng ngà) để ghi điểm. Tránh hạt trấu (màu nâu đậm)!");
        instructionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8B4513; -fx-font-style: italic; -fx-text-alignment: center;");
        instructionLabel.setWrapText(true);

        // Game grid với styling đẹp hơn
        grainGrid = new GridPane();
        grainGrid.setAlignment(Pos.CENTER);
        grainGrid.setHgap(8);
        grainGrid.setVgap(8);
        grainGrid.getStyleClass().add("game-grid");

        // Create 70 grain circles with styling CSS
        for (int i = 0; i < 70; i++) {
            Circle grain = new Circle(22); // Tăng kích thước lên một chút

            // Áp dụng CSS class mặc định
            grain.getStyleClass().addAll("grain-circle", "grain-unclicked");

            final int grainIndex = i;
            grain.setOnMouseClicked(e -> {
                if (currentGameId != null && !grain.isDisabled()) {
                    sendMessage("GAME_ACTION:" + grainIndex);
                    grain.setDisable(true);
                    // Thêm hiệu ứng click
                    grain.setOpacity(0.7);
                }
            });

            grainGrid.add(grain, i % 10, i / 10);
        }

        // Quit button với styling
        Button quitButton = new Button("🚪 Thoát game");
        quitButton.setStyle("-fx-background-color: linear-gradient(to bottom, #DC143C, #B22222); -fx-text-fill: white; -fx-font-weight: bold;");
        quitButton.setOnAction(e -> {
            // Hiển thị xác nhận trước khi thoát
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Xác nhận thoát");
            confirmAlert.setHeaderText("Bạn có chắc muốn thoát game?");
            confirmAlert.setContentText("Nếu thoát bây giờ, bạn sẽ thua cuộc!");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Chỉ gửi message QUIT_GAME, không hiển thị dialog ở đây
                // Dialog sẽ được hiển thị trong handleGameEnded() khi nhận response từ server
                sendMessage("QUIT_GAME");
            }
        });

        gamePlayPane.getChildren().addAll(gameInfoLabel, infoBox, instructionLabel, grainGrid, quitButton);
    }

    private void handleServerMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                final String msg = message;
                Platform.runLater(() -> processServerMessage(msg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> showErrorAlert("Lỗi", "Mất kết nối với server!"));
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
                Scene mainScene = new Scene(mainGamePane, SCENE_WIDTH, SCENE_HEIGHT);

                // Áp dụng CSS cho main scene
                mainScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());

                primaryStage.setScene(mainScene);
                // Request online users after UI has been created and scene is set
                Platform.runLater(() -> sendMessage("GET_ONLINE_USERS"));
                break;

            case "LOGIN_FAILED":
            case "REGISTER_FAILED":
                showErrorAlert("Lỗi", data);
                break;

            case "REGISTER_SUCCESS":
                Platform.runLater(() -> {
                    // Đóng kết nối hiện tại
                    try {
                        if (socket != null && !socket.isClosed()) {
                            socket.close();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Đăng ký thành công! 🎉");
                    successAlert.setHeaderText("Chúc mừng! Tài khoản của bạn đã được tạo");
                    successAlert.setContentText(
                            "Tên đăng nhập: " + data + "\n\n" +
                                    "Bây giờ bạn có thể đăng nhập để bắt đầu chơi!"
                    );
                    successAlert.showAndWait();
                    showLoginForm();
                });
                break;

            case "ONLINE_USERS":
                updateOnlineUsers(data);
                break;

            case "GAME_INVITATION":
                handleGameInvitation(data);
                break;

            case "INVITATION_REJECTED":
                showInfoAlert("Thông báo", data + " đã từ chối lời mời!");
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
            case "INCOMING_MESSAGE":
                String[] chatParts = data.split(":", 2);
                String sender = chatParts[0];
                String content = chatParts[1];
                Platform.runLater(() -> {
                    if (!openChatWindows.containsKey(sender)) {
                        openPrivateChat(sender);
                    }
                    openChatWindows.get(sender).appendMessage(sender + ": " + content);
                });
                break;
            case "SYSTEM_MESSAGE":
                showInfoAlert("Thông báo từ Server", data);
                break;


            case "LEADERBOARD":
                showLeaderboard(data);
                break;
            case "LOBBY_READY":
                String[] lobbyData = data.split(":", 3);
                String lobbyId = lobbyData[0];
                String host = lobbyData[1];
                String[] players = lobbyData[2].split(",");
                currentLobbyId = lobbyId;
                createLobbyUI(lobbyId, host, players);
                Scene lobbyScene = new Scene(gameLobbyPane, SCENE_WIDTH, SCENE_HEIGHT);
                lobbyScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
                primaryStage.setScene(lobbyScene);
                break;
            case "LOBBY_CLOSED":
                showInfoAlert("Thông báo", "Phòng chờ đã bị đóng do người chơi " + data + " đã thoát.");
                backToMainMenu();
                break;
            case "BUFF_ACTIVATED":
                showInfoAlert("Buff!", "Bạn đã nhặt được vật phẩm buff! Điểm của bạn được cộng thêm.");
                break;
            case "DEBUFF_ACTIVATED":
                showInfoAlert("Debuff!", "Bạn đã bị đối thủ làm giảm điểm!");
                break;
            case "DEBUFF_SUCCESS":
                showInfoAlert("Thành công!", "Bạn đã làm giảm điểm của đối thủ!");
                break;
        }
    }

    private void updateOnlineUsers(String data) {
        // Add null check to prevent NullPointerException
        if (userListView == null) {
            // UI not ready yet, ignore this update
            return;
        }

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
        Scene gameScene = new Scene(gamePlayPane, SCENE_WIDTH, SCENE_HEIGHT);

        // Áp dụng CSS cho game scene
        gameScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());

        primaryStage.setScene(gameScene);

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
                    timerLabel.setText("⏰ Thời gian: " + timeRemaining + "s");

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

        // Update grain appearance with CSS classes
        Circle grain = (Circle) grainGrid.getChildren().get(grainIndex);

        // Remove old style classes
        grain.getStyleClass().removeAll("grain-unclicked", "grain-rice", "grain-chaff", "grain-buff", "grain-debuff");

        switch (grainType) {
            case "RICE":
                grain.getStyleClass().add("grain-rice");
                break;
            case "SCORE_BUFF":
                grain.getStyleClass().add("grain-buff");
                break;
            case "SCORE_DEBUFF":
                grain.getStyleClass().add("grain-debuff");
                break;
            default:
                grain.getStyleClass().add("grain-chaff");
                break;
        }

        scoreLabel.setText("🌾 Điểm của bạn: " + currentScore);
    }

    private void handleOpponentScore(String data) {
        String[] parts = data.split(",");
        int opponentScore = Integer.parseInt(parts[1]);
        opponentScoreLabel.setText("⚔️ Điểm đối thủ: " + opponentScore);
    }

    private void handleGameEnded(String data) {
        if (gameTimer != null) {
            gameTimer.cancel();
        }

        String[] parts = data.split(",");
        String winner = parts[0];

        String resultMessage;
        boolean isGameCompleted = true;

        if (winner.equals("QUIT_LOSS")) {
            // Người chơi này đã thoát game và bị thua
            resultMessage = "Bạn đã thoát game giữa chừng!\nKết quả: Thua cuộc\nĐiểm của bạn: " + currentScore;
            isGameCompleted = false;
        } else if (winner.equals("QUIT_WIN")) {
            // Đối thủ đã thoát game, người chơi này thắng
            resultMessage = "Đối thủ đã thoát game!\nChúc mừng! Bạn thắng cuộc!\nĐiểm của bạn: " + currentScore;
            isGameCompleted = true;
        } else if (winner.equals(currentUsername)) {
            resultMessage = "Chúc mừng! Bạn đã thắng!\nĐiểm của bạn: " + currentScore;
            isGameCompleted = true;
        } else if (winner.equals("DRAW")) {
            resultMessage = "Hòa!\nĐiểm của bạn: " + currentScore;
            isGameCompleted = true;
        } else if (winner.equals("QUIT")) {
            resultMessage = "Game kết thúc do bạn thoát!";
            isGameCompleted = false;
        } else {
            resultMessage = "Bạn đã thua!\nĐiểm của bạn: " + currentScore;
            isGameCompleted = true;
        }
        showGameEndDialog("Kết thúc game", resultMessage, isGameCompleted);
    }

    // Lớp helper để chứa dữ liệu bảng xếp hạng
    public static class LeaderboardEntry {
        private final int rank;
        private final String username;
        private final int totalScore;
        private final int gamesPlayed;
        private final int gamesWon;
        private final String winRate;

        public LeaderboardEntry(int rank, String username, int totalScore, int gamesPlayed, int gamesWon, String winRate) {
            this.rank = rank;
            this.username = username;
            this.totalScore = totalScore;
            this.gamesPlayed = gamesPlayed;
            this.gamesWon = gamesWon;
            this.winRate = winRate;
        }

        public int getRank() { return rank; }
        public String getUsername() { return username; }
        public int getTotalScore() { return totalScore; }
        public int getGamesPlayed() { return gamesPlayed; }
        public int getGamesWon() { return gamesWon; }
        public String getWinRate() { return winRate; }
    }

    private void showLeaderboard(String data) {
        leaderboardPane = new VBox(10);
        leaderboardPane.setPadding(new Insets(20));
        leaderboardPane.setAlignment(Pos.CENTER);
        leaderboardPane.getStyleClass().add("main-pane");

        Label title = new Label("🏆 BẢNG XẾP HẠNG 🏆");
        title.getStyleClass().add("title-label");

        TableView<LeaderboardEntry> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<LeaderboardEntry, Integer> rankCol = new TableColumn<>("Hạng");
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        rankCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LeaderboardEntry, String> nameCol = new TableColumn<>("Tên người chơi");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        TableColumn<LeaderboardEntry, Integer> scoreCol = new TableColumn<>("Tổng điểm");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("totalScore"));

        TableColumn<LeaderboardEntry, Integer> playedCol = new TableColumn<>("Số trận");
        playedCol.setCellValueFactory(new PropertyValueFactory<>("gamesPlayed"));

        TableColumn<LeaderboardEntry, Integer> wonCol = new TableColumn<>("Thắng");
        wonCol.setCellValueFactory(new PropertyValueFactory<>("gamesWon"));

        TableColumn<LeaderboardEntry, String> rateCol = new TableColumn<>("Tỷ lệ thắng");
        rateCol.setCellValueFactory(new PropertyValueFactory<>("winRate"));

        tableView.getColumns().addAll(rankCol, nameCol, scoreCol, playedCol, wonCol, rateCol);

        if (!data.isEmpty()) {
            String[] players = data.split(";");
            int rank = 1;
            for (String playerInfo : players) {
                if (!playerInfo.trim().isEmpty()) {
                    String[] p = playerInfo.split(",");
                    tableView.getItems().add(new LeaderboardEntry(rank++, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4] + "%"));
                }
            }
        }

        Button backButton = new Button("Quay lại Menu Chính");
        backButton.setOnAction(e -> backToMainMenu());

        leaderboardPane.getChildren().addAll(title, tableView, backButton);

        Scene scene = new Scene(leaderboardPane, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void backToMainMenu() {
        currentGameId = null;
        opponent = null;
        currentScore = 0;
        // Tạo lại mainGamePane mới để tránh lỗi VBox đã được sử dụng
        createMainGameUI();
        Scene mainScene = new Scene(mainGamePane, SCENE_WIDTH, SCENE_HEIGHT);

        // Áp dụng CSS cho main scene
        mainScene.getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());

        primaryStage.setScene(mainScene);
        // Cập nhật danh sách online users
        sendMessage("GET_ONLINE_USERS");
    }

    private void showGameEndDialog(String title, String message, boolean isGameCompleted) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(message);

        if (isGameCompleted) {
            // Thêm nút cho người chơi lựa chọn sau game kết thúc
            ButtonType playAgainButton = new ButtonType("Chơi tiếp");
            ButtonType mainMenuButton = new ButtonType("Menu chính");
            ButtonType leaderboardButton = new ButtonType("Xem bảng xếp hạng");

            alert.getButtonTypes().setAll(playAgainButton, leaderboardButton, mainMenuButton);
            alert.setContentText("Bạn muốn làm gì tiếp theo?");

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent()) {
                if (result.get() == playAgainButton) {
                    // Quay về menu chính để tìm đối thủ mới
                    backToMainMenu();
                    showErrorAlert("Thông báo", "Hãy chọn đối thủ để chơi tiếp!");
                } else if (result.get() == leaderboardButton) {
                    // Xem bảng xếp hạng trước rồi về menu chính
                    sendMessage("GET_LEADERBOARD");
                    backToMainMenu();
                } else {
                    // Về menu chính
                    backToMainMenu();
                }
            } else {
                backToMainMenu();
            }
        } else {
            // Game bị thoát giữa chừng - chỉ hiện thông báo và về menu
            alert.setContentText(message);
            alert.showAndWait();
            backToMainMenu();
        }
    }

    private void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("error-alert");
        alert.showAndWait();
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/com/example/gamesocket/styles/styles.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("info-alert");
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
