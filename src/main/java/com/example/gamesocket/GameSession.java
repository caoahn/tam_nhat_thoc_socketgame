package com.example.gamesocket;
// GameSession.java
import java.util.*;
import java.util.concurrent.*;

public class GameSession {
    private enum ItemType { RICE, CHAFF, SCORE_BUFF, SCORE_DEBUFF }
    private static final int TOTAL_GRAINS = 100;  // Tăng từ 70 lên 100 hạt
    private static final int TARGET_RICE = 50;
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

    // Biến mới: Theo dõi các hạt gạo đã được nhặt
    private int totalRiceCount = 0; // Tổng số hạt gạo trong game
    private Set<Integer> allRiceClickedPositions; // Các vị trí hạt gạo đã được nhặt bởi bất kỳ ai

    public GameSession(String gameId, String player1, String player2, GameServer server) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        this.server = server;

        this.playerScores = new HashMap<>();
        this.playerClicks = new HashMap<>();
        this.gameEnded = false;
        this.allRiceClickedPositions = new HashSet<>();

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

        // TÍNH TOÁN: 100 hạt tổng cộng
        // - 67 hạt gạo (2/3 của 100 hạt)
        // - 5 buff
        // - 3 debuff
        // = 75 hạt đặc biệt
        // => Còn lại 25 hạt trấu

        // Place rice - ĐẶT 67 HẠT GẠO (2/3 tổng số hạt)
        Set<Integer> positions = new HashSet<>();
        int numRice = 67; // 2/3 của 100 hạt
        for (int i = 0; i < numRice; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (positions.contains(pos));
            positions.add(pos);
            itemTypes[pos] = ItemType.RICE;
            totalRiceCount++; // Đếm tổng số hạt gạo
        }

        // Place score buffs - 5 hạt
        for (int i = 0; i < 5; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (positions.contains(pos));
            positions.add(pos);
            itemTypes[pos] = ItemType.SCORE_BUFF;
        }

        // Place score debuffs - 3 hạt
        for (int i = 0; i < 3; i++) {
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
            // Tạo chuỗi chứa thông tin vị trí các hạt gạo và trấu
            StringBuilder grainPositions = new StringBuilder();
            for (int i = 0; i < TOTAL_GRAINS; i++) {
                if (itemTypes[i] == ItemType.RICE) {
                    grainPositions.append(i).append(":");
                }
            }
            String ricePositions = grainPositions.length() > 0 ?
                grainPositions.substring(0, grainPositions.length() - 1) : "";

            String gameStartMessage = "GAME_STARTED:" + gameId + "," + player2 + "," + GAME_DURATION + "," + ricePositions;
            String gameStartMessage2 = "GAME_STARTED:" + gameId + "," + player1 + "," + GAME_DURATION + "," + ricePositions;

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

        ItemType itemType = itemTypes[grainIndex];
        ClientHandler client = server.getOnlineClients().get(player);
        String opponent = player.equals(player1) ? player2 : player1;
        ClientHandler opponentClient = server.getOnlineClients().get(opponent);

        // Chỉ kiểm tra đã click nếu là hạt gạo (vì gạo sẽ biến mất)
        Set<Integer> playerClickSet = playerClicks.get(player);

        if (itemType == ItemType.RICE) {
            // Hạt gạo: kiểm tra đã click chưa, nếu rồi thì return
            if (playerClickSet.contains(grainIndex)) {
                return; // Already clicked
            }
            playerClickSet.add(grainIndex);
            allRiceClickedPositions.add(grainIndex); // Thêm vào tập hợp các hạt gạo đã được nhặt

            // Cộng điểm
            int newScore = playerScores.get(player) + 1;
            playerScores.put(player, newScore);
            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",RICE," + newScore);
            }
            // GỬI CẢ ĐIỂM SỐ VÀ THÔNG TIN HẠT CHO ĐỐI PHƯƠNG
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_GRAIN_CLICK:" + grainIndex + ",RICE");
                opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + newScore);
            }

            // KIỂM TRA ĐIỀU KIỆN KẾT THÚC GAME
            // 1. Nếu người chơi đạt 20 điểm -> Thắng ngay
            if (newScore >= TARGET_RICE) {
                endGame(player);
                return;
            }

            // 2. Nếu tất cả hạt gạo đã được nhặt hết -> Người có điểm cao hơn thắng
            if (allRiceClickedPositions.size() >= totalRiceCount) {
                endGameAllRiceCollected();
                return;
            }
        } else if (itemType == ItemType.CHAFF) {
            // Hạt trấu: KHÔNG kiểm tra đã click, cho phép click nhiều lần
            // Mỗi lần click trừ 1 điểm
            int newScore = playerScores.get(player) - 1;
            if (newScore < 0) newScore = 0;
            playerScores.put(player, newScore);

            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",CHAFF," + newScore);
            }
            // GỬI THÔNG BÁO CHO ĐỐI PHƯƠNG VỀ VIỆC HẠT TRẤU ĐÃ ĐƯỢC CLICK
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_GRAIN_CLICK:" + grainIndex + ",CHAFF");
                opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + newScore);
            }
        } else if (itemType == ItemType.SCORE_BUFF) {
            // Buff: chỉ cho phép nhặt 1 lần
            if (playerClickSet.contains(grainIndex)) {
                return;
            }
            playerClickSet.add(grainIndex);

            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",SCORE_BUFF," + playerScores.get(player));
            }
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_GRAIN_CLICK:" + grainIndex + ",SCORE_BUFF");
            }
        } else if (itemType == ItemType.SCORE_DEBUFF) {
            // Debuff: chỉ cho phép nhặt 1 lần
            if (playerClickSet.contains(grainIndex)) {
                return;
            }
            playerClickSet.add(grainIndex);

            if (client != null) {
                client.sendMessage("GRAIN_RESULT:" + grainIndex + ",SCORE_DEBUFF," + playerScores.get(player));
            }
            if (opponentClient != null) {
                opponentClient.sendMessage("OPPONENT_GRAIN_CLICK:" + grainIndex + ",SCORE_DEBUFF");
            }
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

    private void endGameAllRiceCollected() {
        if (gameEnded) return;

        gameEnded = true;

        if (gameTimer != null) {
            gameTimer.cancel();
        }

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
        server.endGame(gameId, winner, player1, player2, score1, score2, 0);
    }
}
