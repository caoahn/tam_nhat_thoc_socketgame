package com.example.gamesocket;
// ClientHandler.java
import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private GameServer server;
    private String username;
    private boolean inGame;
    private String currentGameId;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.inGame = false;

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + username);
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "LOGIN":
                handleLogin(data);
                break;
            case "REGISTER":
                handleRegister(data);
                break;
            case "GET_ONLINE_USERS":
                server.sendOnlineUsersToClient(this);
                break;
            case "INVITE":
                server.handleGameInvitation(username, data);
                break;
            case "ACCEPT_INVITATION":
                server.handleInvitationResponse(username, data, true);
                break;
            case "REJECT_INVITATION":
                server.handleInvitationResponse(username, data, false);
                break;
            case "GAME_ACTION":
                handleGameAction(data);
                break;
            case "GET_LEADERBOARD":
                sendMessage(server.getLeaderboard());
                break;
            case "PRIVATE_MESSAGE":
                String[] chatParts = data.split(":", 2);
                if (chatParts.length == 2) {
                    String recipient = chatParts[0];
                    String messageContent = chatParts[1];
                    server.sendPrivateMessage(this.username, recipient, messageContent);
                }
                break;
            case "QUIT_GAME":
                handleQuitGame();
                break;
        }
    }

    private void handleLogin(String data) {
        String[] credentials = data.split(",");
        if (credentials.length == 2) {
            String user = credentials[0];
            String password = credentials[1];

            if (server.authenticateUser(user, password)) {
                if (server.getOnlineClients().containsKey(user)) {
                    sendMessage("LOGIN_FAILED:User already online");
                } else {
                    this.username = user;
                    server.addClient(username, this);
                    sendMessage("LOGIN_SUCCESS:" + username);
                }
            } else {
                sendMessage("LOGIN_FAILED:Invalid credentials");
            }
        }
    }

    private void handleRegister(String data) {
        String[] credentials = data.split(",");
        if (credentials.length == 2) {
            String user = credentials[0];
            String password = credentials[1];

            if (server.registerUser(user, password)) {
                sendMessage("REGISTER_SUCCESS");
            } else {
                sendMessage("REGISTER_FAILED:Username already exists");
            }
        }
    }

    private void handleGameAction(String data) {
        if (currentGameId != null) {
            try {
                int grainIndex = Integer.parseInt(data);
                server.handleGameAction(currentGameId, username, grainIndex);
            } catch (NumberFormatException e) {
                System.out.println("Invalid game action from " + username + ": " + data);
            }
        }
    }

    private void handleQuitGame() {
        if (inGame && currentGameId != null) {
            // Thông báo server xử lý người chơi thoát game
            // Server sẽ tự động cho người còn lại thắng
            server.handlePlayerQuit(currentGameId, username);

            // Reset trạng thái của client này
            setInGame(false);
            setCurrentGameId(null);
            server.broadcastOnlineUsers();
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public String getCurrentGameId() {
        return currentGameId;
    }

    public void setCurrentGameId(String currentGameId) {
        this.currentGameId = currentGameId;
    }

    public String getUsername() {
        return username;
    }

    private void cleanup() {
        try {
            if (username != null) {
                server.removeClient(username);
            }

            if (inGame && currentGameId != null) {
                handleQuitGame();
            }

            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}