package com.example.gamesocket;
// GameSession.java
import java.util.*;
import java.util.concurrent.*;

public class GameSession {
    private enum ItemType { RICE, CHAFF, SCORE_BUFF, SCORE_DEBUFF }
    private static final int TOTAL_GRAINS = 70;
    private static final int TARGET_RICE = 20;
    private static final int GAME_DURATION = 50; // seconds

    private String gameId;
    private String player1;
    private String player2;
    private GameServer server;

    private Map<String, Integer> playerScores;
    private Map<String, Set<Integer>> playerClicks;
    private ItemType[] itemTypes;
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

        initializeItems();
    }

    private void initializeItems() {
        itemTypes = new ItemType[TOTAL_GRAINS];
        Arrays.fill(itemTypes, ItemType.CHAFF); // Default to chaff
        Random random = new Random();

        // Place rice
        Set<Integer> positions = new HashSet<>();
        for (int i = 0; i < TARGET_RICE + 5; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (positions.contains(pos));
            positions.add(pos);
            itemTypes[pos] = ItemType.RICE;
        }

        // Place score buffs
        for (int i = 0; i < 3; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (positions.contains(pos));
            positions.add(pos);
            itemTypes[pos] = ItemType.SCORE_BUFF;
        }

        // Place score debuffs
        for (int i = 0; i < 2; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (positions.contains(pos));
            positions.add(pos);
            itemTypes[pos] = ItemType.SCORE_DEBUFF;
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
            return; // Already clicked
        }

        playerClickSet.add(grainIndex);

        ItemType itemType = itemTypes[grainIndex];
        ClientHandler client = server.getOnlineClients().get(player);
        String opponent = player.equals(player1) ? player2 : player1;
        ClientHandler opponentClient = server.getOnlineClients().get(opponent);

        switch (itemType) {
            case RICE:
                int newScore = playerScores.get(player) + 1;
                playerScores.put(player, newScore);
                if (client != null) {
                    client.sendMessage("GRAIN_RESULT:" + grainIndex + ",RICE," + newScore);
                }
                if (opponentClient != null) {
                    opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + newScore);
                }
                if (newScore >= TARGET_RICE) {
                    endGame(player);
                    return;
                }
                break;
            case SCORE_BUFF:
                // Chỉ thông báo nhặt được, không cộng điểm ngay
                if (client != null) {
                    client.sendMessage("GRAIN_RESULT:" + grainIndex + ",SCORE_BUFF," + playerScores.get(player));
                }
                break;
            case SCORE_DEBUFF:
                // Chỉ thông báo nhặt được, không trừ điểm đối thủ ngay
                if (client != null) {
                    client.sendMessage("GRAIN_RESULT:" + grainIndex + ",SCORE_DEBUFF," + playerScores.get(player));
                }
                break;
            case CHAFF:
                if (client != null) {
                    client.sendMessage("GRAIN_RESULT:" + grainIndex + ",CHAFF," + playerScores.get(player));
                }
                break;
        }
    }

    /**
     * Xử lý khi người chơi sử dụng buff hoặc debuff từ inventory
     */
    public synchronized void handleUseBuffDebuff(String player, boolean isBuff) {
        if (gameEnded) return;

        ClientHandler client = server.getOnlineClients().get(player);
        String opponent = player.equals(player1) ? player2 : player1;
        ClientHandler opponentClient = server.getOnlineClients().get(opponent);

        if (isBuff) {
            // Buff: Cộng 3 điểm cho người chơi
            int newScore = playerScores.get(player) + 3;
            playerScores.put(player, newScore);

            // Gửi cho người chơi: chỉ BUFF_ACTIVATED (không gửi OPPONENT_SCORE)
            if (client != null) {
                client.sendMessage("BUFF_ACTIVATED:+" + newScore);
            }
            // Gửi cho đối thủ: cập nhật điểm của người chơi vừa dùng buff
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + newScore);
            }

            // Kiểm tra điều kiện thắng
            if (newScore >= TARGET_RICE) {
                endGame(player);
                return;
            }
        } else {
            // Debuff: Trừ 2 điểm của đối thủ
            int opponentScore = playerScores.get(opponent) - 2;
            if (opponentScore < 0) opponentScore = 0;
            playerScores.put(opponent, opponentScore);

            // Gửi cho người chơi: thông báo thành công VÀ cập nhật điểm đối thủ
            if (client != null) {
                client.sendMessage("DEBUFF_SUCCESS:Đã giảm điểm đối thủ!");
                client.sendMessage("OPPONENT_SCORE:" + opponent + "," + opponentScore);
            }
            // Gửi cho đối thủ: thông báo bị debuff (điểm tự cập nhật qua DEBUFF_ACTIVATED)
            if (opponentClient != null) {
                opponentClient.sendMessage("DEBUFF_ACTIVATED:-" + opponentScore);
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

