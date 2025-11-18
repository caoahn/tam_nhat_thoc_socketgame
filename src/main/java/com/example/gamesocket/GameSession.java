package com.example.gamesocket;
// GameSession.java
import java.util.*;
import java.util.concurrent.*;

public class GameSession {
    private enum GrainType { RICE, CHAFF }
    private enum PowerupType { NONE, SCORE_BUFF, SCORE_DEBUFF }
    private static final int TOTAL_GRAINS = 100;
    private static final int TARGET_RICE = 50;
    private static final int GAME_DURATION = 100; // seconds

    private String gameId;
    private String player1;
    private String player2;
    private GameServer server;

    private Map<String, Integer> playerScores;
    private Map<String, Set<Integer>> playerClicks;
    private GrainType[] grainTypes; // Mảng lưu loại hạt (gạo/thóc)
    private PowerupType[] powerupTypes; // Mảng lưu loại power-up (buff/debuff/none)
    private Timer gameTimer;
    private long gameStartTime;
    private boolean gameEnded;

    // Biến mới: Theo dõi các hạt gạo đã được nhặt
    private int totalRiceCount = 0;
    private Set<Integer> allRiceClickedPositions;

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
        grainTypes = new GrainType[TOTAL_GRAINS];
        powerupTypes = new PowerupType[TOTAL_GRAINS];
        Arrays.fill(grainTypes, GrainType.CHAFF); // Default to chaff
        Arrays.fill(powerupTypes, PowerupType.NONE); // Default to no powerup
        Random random = new Random();

        // TÍNH TOÁN: 100 hạt tổng cộng
        // - 67 hạt gạo (2/3 của 100 hạt)
        // - 33 hạt trấu
        // - 5 buff (có thể nằm ở bất kỳ hạt nào, kể cả hạt gạo)
        // - 3 debuff (có thể nằm ở bất kỳ hạt nào, kể cả hạt gạo)

        // Place rice - ĐẶT 67 HẠT GẠO
        Set<Integer> ricePositions = new HashSet<>();
        int numRice = 67;
        for (int i = 0; i < numRice; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (ricePositions.contains(pos));
            ricePositions.add(pos);
            grainTypes[pos] = GrainType.RICE;
            totalRiceCount++;
        }

        // Place score buffs - 5 hạt (có thể nằm ở BẤT KỲ vị trí nào, kể cả hạt gạo)
        Set<Integer> powerupPositions = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (powerupPositions.contains(pos));
            powerupPositions.add(pos);
            powerupTypes[pos] = PowerupType.SCORE_BUFF;
        }

        // Place score debuffs - 3 hạt (có thể nằm ở BẤT KỲ vị trí nào, kể cả hạt gạo)
        for (int i = 0; i < 3; i++) {
            int pos;
            do {
                pos = random.nextInt(TOTAL_GRAINS);
            } while (powerupPositions.contains(pos));
            powerupPositions.add(pos);
            powerupTypes[pos] = PowerupType.SCORE_DEBUFF;
        }
    }

    public void startGame() {
        gameStartTime = System.currentTimeMillis();

        // Gửi thông tin bắt đầu game cho cả hai người chơi
        ClientHandler client1 = server.getOnlineClients().get(player1);
        ClientHandler client2 = server.getOnlineClients().get(player2);

        if (client1 != null && client2 != null) {
            // Tạo chuỗi chứa thông tin vị trí các hạt gạo
            StringBuilder grainPositions = new StringBuilder();
            for (int i = 0; i < TOTAL_GRAINS; i++) {
                if (grainTypes[i] == GrainType.RICE) {
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

        GrainType grainType = grainTypes[grainIndex];
        PowerupType powerupType = powerupTypes[grainIndex];
        ClientHandler client = server.getOnlineClients().get(player);
        String opponent = player.equals(player1) ? player2 : player1;
        ClientHandler opponentClient = server.getOnlineClients().get(opponent);

        Set<Integer> playerClickSet = playerClicks.get(player);

        // Biến theo dõi điểm số
        int currentScore = playerScores.get(player);
        boolean hasClicked = playerClickSet.contains(grainIndex);

        // Xử lý hạt gạo/thóc
        if (grainType == GrainType.RICE) {
            // Hạt gạo: kiểm tra đã click chưa
            if (hasClicked) {
                return; // Already clicked
            }
            playerClickSet.add(grainIndex);
            allRiceClickedPositions.add(grainIndex);

            // Cộng 1 điểm cho hạt gạo
            currentScore += 1;
            playerScores.put(player, currentScore);

        } else if (grainType == GrainType.CHAFF) {
            // Hạt trấu: KHÔNG kiểm tra đã click nếu KHÔNG có powerup
            // Nếu có powerup thì chỉ click được 1 lần
            if (powerupType != PowerupType.NONE) {
                if (hasClicked) {
                    return; // Đã click hạt có powerup rồi
                }
                playerClickSet.add(grainIndex);
            }

            // Trừ 1 điểm cho hạt trấu
            currentScore -= 1;
            if (currentScore < 0) currentScore = 0;
            playerScores.put(player, currentScore);
        }

        // Xử lý buff/debuff (nếu có) - CHỈ THU THẬP VÀO INVENTORY
        if (powerupType == PowerupType.SCORE_BUFF) {
            // Buff: Không tự động kích hoạt, chỉ thu thập vào inventory
            // (Điểm số không thay đổi ở đây)

        } else if (powerupType == PowerupType.SCORE_DEBUFF) {
            // Debuff: Không tự động kích hoạt, chỉ thu thập vào inventory
            // (Điểm số đối thủ không thay đổi ở đây)
        }

        // Gửi kết quả cho người chơi
        String resultType = grainType == GrainType.RICE ? "RICE" : "CHAFF";
        if (powerupType == PowerupType.SCORE_BUFF) {
            resultType += "_BUFF";
        } else if (powerupType == PowerupType.SCORE_DEBUFF) {
            resultType += "_DEBUFF";
        }

        if (client != null) {
            client.sendMessage("GRAIN_RESULT:" + grainIndex + "," + resultType + "," + currentScore);
        }

        // Gửi thông tin cho đối phương
        if (opponentClient != null) {
            opponentClient.sendMessage("OPPONENT_GRAIN_CLICK:" + grainIndex + "," + resultType);
            opponentClient.sendMessage("OPPONENT_SCORE:" + player + "," + currentScore);
        }

        // KIỂM TRA ĐIỀU KIỆN KẾT THÚC GAME
        // 1. Nếu người chơi đạt 50 điểm -> Thắng ngay
        if (currentScore >= TARGET_RICE) {
            endGame(player);
            return;
        }

        // 2. Nếu tất cả hạt gạo đã được nhặt hết -> Người có điểm cao hơn thắng
        if (allRiceClickedPositions.size() >= totalRiceCount) {
            endGameAllRiceCollected();
            return;
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
