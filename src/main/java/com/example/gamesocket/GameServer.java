package com.example.gamesocket;
// GameServer.java
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private static final int PORT = 8888;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/rice_game";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    private ServerSocket serverSocket;
    private Map<String, ClientHandler> onlineClients;
    private Map<String, GameSession> activeSessions;
    private Map<String, GameLobby> activeLobbies; // Add this line
    private ExecutorService executor;
    private AtomicInteger gameIdCounter;

    public GameServer() {
        onlineClients = new ConcurrentHashMap<>();
        activeSessions = new ConcurrentHashMap<>();
        activeLobbies = new ConcurrentHashMap<>(); // Add this line
        executor = Executors.newCachedThreadPool();
        gameIdCounter = new AtomicInteger(1);
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Tạo bảng users
            String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password VARCHAR(100) NOT NULL,
                    total_score INT DEFAULT 0,
                    games_played INT DEFAULT 0,
                    games_won INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;

            // Tạo bảng game_results
            String createGameResultsTable = """
                CREATE TABLE IF NOT EXISTS game_results (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    game_id VARCHAR(50) NOT NULL,
                    player1 VARCHAR(50) NOT NULL,
                    player2 VARCHAR(50) NOT NULL,
                    winner VARCHAR(50),
                    player1_score INT DEFAULT 0,
                    player2_score INT DEFAULT 0,
                    duration_seconds INT DEFAULT 0,
                    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;

            Statement stmt = conn.createStatement();
            stmt.execute(createUsersTable);
            stmt.execute(createGameResultsTable);

            conn.close();
            System.out.println("Database initialized successfully");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Game Server started on port " + PORT);

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                executor.submit(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendPrivateMessage(String sender, String recipient, String message) {
        // Tìm ClientHandler của người nhận trong danh sách online
        ClientHandler recipientHandler = onlineClients.get(recipient);

        if (recipientHandler != null) {
            // Nếu người nhận đang online, tạo message và gửi cho họ
            String forwardMessage = "INCOMING_MESSAGE:" + sender + ":" + message;
            recipientHandler.sendMessage(forwardMessage);
        } else {
            // Nếu người nhận không online, gửi lại thông báo lỗi cho người gửi
            ClientHandler senderHandler = onlineClients.get(sender);
            if (senderHandler != null) {
                // Chúng ta sẽ dùng một message hệ thống mới để client xử lý
                senderHandler.sendMessage("SYSTEM_MESSAGE:Người dùng '" + recipient + "' không trực tuyến hoặc đã thoát.");
            }
        }
    }

    public synchronized void addClient(String username, ClientHandler handler) {
        onlineClients.put(username, handler);
        broadcastOnlineUsers();
        System.out.println("User " + username + " connected. Online users: " + onlineClients.size());
    }

    public synchronized void removeClient(String username) {
        onlineClients.remove(username);
        broadcastOnlineUsers();
        System.out.println("User " + username + " disconnected. Online users: " + onlineClients.size());
    }

    public void broadcastOnlineUsers() {
        String userListMessage = buildOnlineUsersMessage();
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(userListMessage);
        }
    }

    public void sendOnlineUsersToClient(ClientHandler client) {
        String userListMessage = buildOnlineUsersMessage();
        client.sendMessage(userListMessage);
    }

    private String buildOnlineUsersMessage() {
        StringBuilder userList = new StringBuilder("ONLINE_USERS:");
        for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            String status = handler.isInGame() ? "BUSY" : "FREE";
            int totalScore = getUserTotalScore(username);
            userList.append(username).append(",").append(totalScore).append(",").append(status).append(";");
        }
        return userList.toString();
    }

    public void handleGameInvitation(String inviter, String invited) {
        ClientHandler invitedClient = onlineClients.get(invited);
        ClientHandler inviterClient = onlineClients.get(inviter);

        if (invitedClient != null) {
            if (invitedClient.isInGame()) {
                // Người được mời đang trong game hoặc lobby
                if (inviterClient != null) {
                    inviterClient.sendMessage("SYSTEM_MESSAGE:Không thể mời " + invited + "! Họ đang bận (trong trận hoặc phòng chờ).");
                }
            } else {
                // Gửi lời mời
                invitedClient.sendMessage("GAME_INVITATION:" + inviter);
            }
        } else {
            // Người được mời không online
            if (inviterClient != null) {
                inviterClient.sendMessage("SYSTEM_MESSAGE:Không thể mời " + invited + "! Họ không trực tuyến.");
            }
        }
    }

    public void handleInvitationResponse(String invited, String inviter, boolean accepted) {
        ClientHandler inviterClient = onlineClients.get(inviter);
        if (inviterClient != null) {
            if (accepted) {
                // Create a new lobby
                String lobbyId = "LOBBY_" + gameIdCounter.getAndIncrement();
                GameLobby lobby = new GameLobby(lobbyId, inviter, this);
                lobby.addPlayer(invited);
                activeLobbies.put(lobbyId, lobby);

                // Notify players that the lobby is ready
                lobby.startLobby();
            } else {
                inviterClient.sendMessage("INVITATION_REJECTED:" + invited);
            }
        }
    }

    public void handleStartGameRequest(String lobbyId, String player) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            if (lobby.getHost().equals(player)) {
                // Kiểm tra số lượng người chơi trước khi bắt đầu
                if (lobby.getPlayerCount() < 2) {
                    ClientHandler client = onlineClients.get(player);
                    if (client != null) {
                        client.sendMessage("SYSTEM_MESSAGE:Không thể bắt đầu game! Cần ít nhất 2 người chơi.");
                    }
                    return;
                }
                startGame(lobby);
            } else {
                // Notify player that they are not the host
                ClientHandler client = onlineClients.get(player);
                if (client != null) {
                    client.sendMessage("SYSTEM_MESSAGE:Chỉ host mới có thể bắt đầu game.");
                }
            }
        }
    }

    private void startGame(GameLobby lobby) {
        String gameId = "GAME_" + gameIdCounter.getAndIncrement();
        List<String> players = lobby.getPlayers();
        String player1 = players.get(0);
        String player2 = players.get(1);

        GameSession session = new GameSession(gameId, player1, player2, this);
        activeSessions.put(gameId, session);

        ClientHandler client1 = onlineClients.get(player1);
        ClientHandler client2 = onlineClients.get(player2);

        if (client1 != null && client2 != null) {
            client1.setInGame(true);
            client2.setInGame(true);
            client1.setCurrentGameId(gameId);
            client2.setCurrentGameId(gameId);

            session.startGame();
            broadcastOnlineUsers();
        }

        activeLobbies.remove(lobby.getLobbyId());
    }

    public void handleLobbyClose(String lobbyId, String disconnectedPlayer) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            String host = lobby.getHost();

            // Nếu host rời phòng, hủy lobby và đuổi tất cả người chơi
            if (disconnectedPlayer.equals(host)) {
                System.out.println("Host " + disconnectedPlayer + " left lobby " + lobbyId + ". Closing lobby.");

                // Thông báo cho tất cả người chơi còn lại
                for (String player : lobby.getPlayers()) {
                    if (!player.equals(disconnectedPlayer)) {
                        ClientHandler client = onlineClients.get(player);
                        if (client != null) {
                            client.sendMessage("LOBBY_CLOSED:" + disconnectedPlayer);
                            client.setCurrentLobbyId(null);
                            client.setInGame(false); // Reset trạng thái về FREE
                        }
                    }
                }

                // Xóa lobby hoàn toàn
                activeLobbies.remove(lobbyId);

                // Reset trạng thái của host
                ClientHandler hostClient = onlineClients.get(disconnectedPlayer);
                if (hostClient != null) {
                    hostClient.setCurrentLobbyId(null);
                    hostClient.setInGame(false); // Reset trạng thái về FREE
                }
            }
            // Nếu người chơi thường rời phòng
            else {
                System.out.println("Player " + disconnectedPlayer + " left lobby " + lobbyId);

                lobby.removePlayer(disconnectedPlayer);

                // Reset trạng thái của người rời
                ClientHandler playerClient = onlineClients.get(disconnectedPlayer);
                if (playerClient != null) {
                    playerClient.setCurrentLobbyId(null);
                    playerClient.setInGame(false); // Reset trạng thái về FREE
                }

                // Gửi thông báo cho những người còn lại và cập nhật UI
                if (lobby.getPlayerCount() >= 1) {
                    // Cập nhật danh sách người chơi cho những người còn lại
                    lobby.notifyPlayersUpdate();
                }
            }

            // Broadcast cập nhật danh sách online users
            broadcastOnlineUsers();
        }
    }

    public void handleLobbyLeave(String lobbyId, String playerName) {
        // Gọi lại handleLobbyClose vì logic giống nhau
        handleLobbyClose(lobbyId, playerName);
    }

    public void handleGameAction(String gameId, String player, int grainIndex) {
        GameSession session = activeSessions.get(gameId);
        if (session != null) {
            session.handlePlayerAction(player, grainIndex);
        }
    }

    public void handleUseBuffDebuff(String gameId, String player, boolean isBuff) {
        GameSession session = activeSessions.get(gameId);
        if (session != null) {
            session.handleUseBuffDebuff(player, isBuff);
        }
    }

    /**
     * Xử lý khi một người chơi thoát game giữa chừng
     * @param gameId ID của game session
     * @param quittingPlayer Tên người chơi thoát game
     */
    public void handlePlayerQuit(String gameId, String quittingPlayer) {
        GameSession session = activeSessions.get(gameId);
        if (session != null) {
            session.handlePlayerQuit(quittingPlayer);
        }
    }

    public void endGame(String gameId, String winner, String player1, String player2,
                        int score1, int score2, int duration) {
        activeSessions.remove(gameId);

        ClientHandler client1 = onlineClients.get(player1);
        ClientHandler client2 = onlineClients.get(player2);

        if (client1 != null) {
            client1.setInGame(false);
            client1.setCurrentGameId(null);
        }
        if (client2 != null) {
            client2.setInGame(false);
            client2.setCurrentGameId(null);
        }

        saveGameResult(gameId, player1, player2, winner, score1, score2, duration);
        broadcastOnlineUsers();
    }

    private void saveGameResult(String gameId, String player1, String player2,
                                String winner, int score1, int score2, int duration) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Lưu kết quả game
            String insertResult = """
                INSERT INTO game_results (game_id, player1, player2, winner, player1_score, player2_score, duration_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

            PreparedStatement stmt = conn.prepareStatement(insertResult);
            stmt.setString(1, gameId);
            stmt.setString(2, player1);
            stmt.setString(3, player2);
            stmt.setString(4, winner);
            stmt.setInt(5, score1);
            stmt.setInt(6, score2);
            stmt.setInt(7, duration);
            stmt.executeUpdate();

            // Cập nhật thống kê người chơi
            updatePlayerStats(player1, winner.equals(player1), score1);
            updatePlayerStats(player2, winner.equals(player2), score2);

            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updatePlayerStats(String username, boolean won, int score) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String updateStats = """
                UPDATE users SET 
                total_score = total_score + ?,
                games_played = games_played + 1,
                games_won = games_won + ?
                WHERE username = ?
            """;

            PreparedStatement stmt = conn.prepareStatement(updateStats);
            stmt.setInt(1, score);
            stmt.setInt(2, won ? 1 : 0);
            stmt.setString(3, username);
            stmt.executeUpdate();

            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean authenticateUser(String username, String password) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String query = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();

            conn.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean registerUser(String username, String password) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String insert = "INSERT INTO users (username, password) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(insert);
            stmt.setString(1, username);
            stmt.setString(2, password);

            stmt.executeUpdate();
            conn.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getUserTotalScore(String username) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String query = "SELECT total_score FROM users WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();
            int score = 0;
            if (rs.next()) {
                score = rs.getInt("total_score");
            }

            conn.close();
            return score;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public String getLeaderboard() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String query = """
                SELECT username, total_score, games_played, games_won,
                CASE WHEN games_played > 0 THEN (games_won * 100.0 / games_played) ELSE 0 END as win_rate
                FROM users 
                WHERE games_played > 0
                ORDER BY win_rate DESC, total_score DESC
                LIMIT 20
            """;

            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            StringBuilder leaderboard = new StringBuilder("LEADERBOARD:");
            while (rs.next()) {
                String username = rs.getString("username");
                int totalScore = rs.getInt("total_score");
                int gamesPlayed = rs.getInt("games_played");
                int gamesWon = rs.getInt("games_won");
                double winRate = rs.getDouble("win_rate");

                leaderboard.append(username).append(",")
                        .append(totalScore).append(",")
                        .append(gamesPlayed).append(",")
                        .append(gamesWon).append(",")
                        .append(String.format("%.2f", winRate)).append(";");
            }

            conn.close();
            return leaderboard.toString();
        } catch (SQLException e) {
            e.printStackTrace();
            return "LEADERBOARD:";
        }
    }

    public Map<String, ClientHandler> getOnlineClients() {
        return onlineClients;
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executor.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

