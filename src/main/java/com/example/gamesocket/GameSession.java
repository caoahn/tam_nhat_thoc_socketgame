package com.example.gamesocket;
// GameSession.java
import java.util.*;
import java.util.concurrent.*;

public class GameSession {
    private static final int TOTAL_GRAINS = 70;
    private static final int TARGET_RICE = 20;
    private static final int GAME_DURATION = 15; // seconds

    private String gameId;
    private String player1;
    private String player2;
    private GameServer server;

    private Map<String, Integer> playerScores;
    private Map<String, Set<Integer>> playerClicks;
    private boolean[] grainTypes; // true = rice, false = chaff
    private Timer gameTimer;
    private long gameStartTime;
    private boolean gameEnded;

    public GameSession(String gameId, String player1, String player2, GameServer server) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        this.server = server;

        this.playerScores = new HashMap<>();
        this.playerClicks = new HashMap<>();
        this.gameEnded = false;

        playerScores.put(player1, 0);
        playerScores.put(player2, 0);
        playerClicks.put(player1, new HashSet<>());
        playerClicks.put(player2, new HashSet<>());

        initializeGrains();
    }

    private void initializeGrains() {
        grainTypes = new boolean[TOTAL_GRAINS];
        Random random = new Random();

        // Đảm bảo có ít nhất TARGET_RICE hạt thóc
        Set<Integer> ricePositions = new HashSet<>();
        while (ricePositions.size() < TARGET_RICE + 5) { // Thêm một ít hạt thóc
            ricePositions.add(random.nextInt(TOTAL_GRAINS));
        }

        for (int pos : ricePositions) {
            grainTypes[pos] = true; // true = rice
        }
    }

    public void startGame() {
        gameStartTime = System.currentTimeMillis();

        // Gửi thông tin bắt đầu game cho cả hai người chơi
        ClientHandler client1 = server.getOnlineClients().get(player1);
        ClientHandler client2 = server.getOnlineClients().get(player2);

        if (client1 != null && client2 != null) {
            String gameStartMessage = "GAME_STARTED:" + gameId + "," + player2 + "," + GAME_DURATION;
            String gameStartMessage2 = "GAME_STARTED:" + gameId + "," + player1 + "," + GAME_DURATION;

            client1.sendMessage(gameStartMessage);
            client2.sendMessage(gameStartMessage2);

            // Bắt đầu timer cho game
            startGameTimer();
        }
    }

    private void startGameTimer() {
        gameTimer = new Timer();
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!gameEnded) {
                    endGameByTimeout();
                }
            }
        }, GAME_DURATION * 1000);
    }

    public synchronized void handlePlayerAction(String player, int grainIndex) {
        if (gameEnded || grainIndex < 0 || grainIndex >= TOTAL_GRAINS) {
            return;
        }

        Set<Integer> playerClickSet = playerClicks.get(player);
        if (playerClickSet.contains(grainIndex)) {
            return; // Đã click rồi
        }

        playerClickSet.add(grainIndex);

        boolean isRice = grainTypes[grainIndex];
        if (isRice) {
            int newScore = playerScores.get(player) + 1;
            playerScores.put(player, newScore);

            // Gửi kết quả click cho người chơi
            ClientHandler client = server.getOnlineClients().get(player);
            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",RICE," + newScore);
            }

            // Thông báo cho đối thủ
            String opponent = player.equals(player1) ? player2 : player1;
            ClientHandler opponentClient = server.getOnlineClients().get(opponent);
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + newScore);
            }

            // Kiểm tra điều kiện thắng
            if (newScore >= TARGET_RICE) {
                endGame(player);
                return;
            }
        } else {
            // Hạt gạo/trấu
            ClientHandler client = server.getOnlineClients().get(player);
            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",CHAFF," + playerScores.get(player));
            }
        }
    }

    private void endGameByTimeout() {
        if (gameEnded) return;

        int score1 = playerScores.get(player1);
        int score2 = playerScores.get(player2);

        String winner;
        if (score1 > score2) {
            winner = player1;
        } else if (score2 > score1) {
            winner = player2;
        } else {
            winner = "DRAW";
        }

        endGame(winner);
    }

    private void endGame(String winner) {
        if (gameEnded) return;

        gameEnded = true;

        if (gameTimer != null) {
            gameTimer.cancel();
        }

        int score1 = playerScores.get(player1);
        int score2 = playerScores.get(player2);

        long duration = (System.currentTimeMillis() - gameStartTime) / 1000;

        // Gửi kết quả cho cả hai người chơi
        ClientHandler client1 = server.getOnlineClients().get(player1);
        ClientHandler client2 = server.getOnlineClients().get(player2);

        String gameEndMessage = "GAME_ENDED:" + winner + "," + score1 + "," + score2;

        if (client1 != null) {
            client1.sendMessage(gameEndMessage);
        }
        if (client2 != null) {
            client2.sendMessage(gameEndMessage);
        }

        // Thông báo server kết thúc game
        server.endGame(gameId, winner, player1, player2, score1, score2, (int)duration);
    }

    /**
     * Xử lý khi một người chơi thoát game giữa chừng
     * Người thoát sẽ thua, người còn lại sẽ thắng
     */
    public synchronized void handlePlayerQuit(String quittingPlayer) {
        if (gameEnded) return;

        gameEnded = true;

        if (gameTimer != null) {
            gameTimer.cancel();
        }

        // Xác định người thắng (người không thoát)
        String winner = quittingPlayer.equals(player1) ? player2 : player1;

        int score1 = playerScores.get(player1);
        int score2 = playerScores.get(player2);

        long duration = (System.currentTimeMillis() - gameStartTime) / 1000;

        // Gửi thông báo kết thúc game cho cả hai người chơi
        ClientHandler quittingClient = server.getOnlineClients().get(quittingPlayer);
        ClientHandler winnerClient = server.getOnlineClients().get(winner);

        // Thông báo cho người thoát game (thua)
        if (quittingClient != null) {
            quittingClient.sendMessage("GAME_ENDED:QUIT_LOSS," + score1 + "," + score2);
        }

        // Thông báo cho người thắng
        if (winnerClient != null) {
            winnerClient.sendMessage("GAME_ENDED:QUIT_WIN," + score1 + "," + score2);
        }

        // Thông báo server kết thúc game với người thắng
        server.endGame(gameId, winner, player1, player2, score1, score2, (int)duration);
    }
}

