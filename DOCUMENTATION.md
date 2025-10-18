# TÀI LIỆU DỰ ÁN GAME TẤM NHẶT THÓC

## TỔNG QUAN DỰ ÁN

Dự án "Game Tấm Nhặt Thóc" là một ứng dụng game trực tuyến đa người chơi được xây dựng bằng Java với JavaFX cho giao diện người dùng và Socket cho giao tiếp mạng. Game mô phỏng việc nhặt thóc, người chơi cần phân biệt giữa hạt thóc và trấu để ghi điểm.

### Kiến trúc hệ thống:
- **Client-Server Architecture**: Sử dụng Socket TCP
- **Database**: MySQL để lưu trữ thông tin người dùng và kết quả game
- **UI Framework**: JavaFX với CSS styling
- **Threading**: Multithreading với ExecutorService
- **Chat System**: Hệ thống chat riêng tư giữa người chơi

---

## 1. FILE GAMESERVER.JAVA

### Mục đích:
Server chính của game, quản lý tất cả kết nối client, phiên game, database và hệ thống chat.

### Thuộc tính chính:
```java
private static final int PORT = 8888;                    // Cổng server
private static final String DB_URL = "jdbc:mysql://localhost:3306/rice_game";
private ServerSocket serverSocket;                       // Socket server
private Map<String, ClientHandler> onlineClients;       // Danh sách client online
private Map<String, GameSession> activeSessions;        // Các phiên game đang hoạt động
private ExecutorService executor;                        // Thread pool
private AtomicInteger gameIdCounter;                     // Đếm ID game
```

### Workflow chính:

#### 1. Khởi tạo Server (Constructor):
```
GameServer() → initializeDatabase() → Tạo thread pool → Khởi tạo Maps
```

#### 2. Khởi động Server:
```
start() → Tạo ServerSocket → Lắng nghe kết nối → Tạo ClientHandler cho mỗi client → Submit vào thread pool
```

### Chi tiết các function:

#### **initializeDatabase()**
- **Mục đích**: Khởi tạo database và tạo bảng cần thiết
- **Workflow**:
  1. Kết nối MySQL database
  2. Tạo bảng `users` (thông tin người dùng)
  3. Tạo bảng `game_results` (kết quả game)
- **Bảng users**: id, username, password, total_score, games_played, games_won, created_at
- **Bảng game_results**: id, game_id, player1, player2, winner, player1_score, player2_score, duration_seconds, played_at

#### **Client Management Functions:**

##### **addClient(String username, ClientHandler handler)**
- **Mục đích**: Thêm client mới vào danh sách online
- **Workflow**:
  1. Thêm vào `onlineClients` map
  2. Gọi `broadcastOnlineUsers()` để cập nhật danh sách cho tất cả client
  3. In log số lượng user online

##### **removeClient(String username)**
- **Mục đích**: Xóa client khỏi danh sách online
- **Workflow**: 
  1. Xóa khỏi `onlineClients` map
  2. Gọi `broadcastOnlineUsers()`
  3. In log số lượng user còn lại

##### **broadcastOnlineUsers()**
- **Mục đích**: Gửi danh sách user online cho tất cả client
- **Workflow**:
  1. Gọi `buildOnlineUsersMessage()` để tạo message
  2. Gửi message cho tất cả client trong `onlineClients`

##### **buildOnlineUsersMessage()**
- **Mục đích**: Tạo chuỗi chứa thông tin tất cả user online
- **Format**: `"ONLINE_USERS:username1,score1,status1;username2,score2,status2;..."`
- **Status**: "BUSY" (đang chơi) hoặc "FREE" (rảnh)

#### **Game Management Functions:**

##### **handleGameInvitation(String inviter, String invited)**
- **Mục đích**: Xử lý lời mời chơi game
- **Workflow**:
  1. Tìm client được mời trong `onlineClients`
  2. Kiểm tra client không đang chơi game
  3. Gửi message `"GAME_INVITATION:" + inviter` cho client được mời

##### **handleInvitationResponse(String invited, String inviter, boolean accepted)**
- **Mục đích**: Xử lý phản hồi lời mời
- **Workflow**:
  - Nếu chấp nhận: Gọi `startGame(inviter, invited)`
  - Nếu từ chối: Gửi `"INVITATION_REJECTED:" + invited` cho người mời

##### **startGame(String player1, String player2)**
- **Mục đích**: Bắt đầu một phiên game mới
- **Workflow**:
  1. Tạo gameId unique: `"GAME_" + gameIdCounter.getAndIncrement()`
  2. Tạo `GameSession` mới
  3. Thêm vào `activeSessions`
  4. Set trạng thái `inGame = true` cho cả hai client
  5. Set `currentGameId` cho cả hai client
  6. Gọi `session.startGame()`
  7. Broadcast cập nhật danh sách user (status BUSY)

##### **handleGameAction(String gameId, String player, int grainIndex)**
- **Mục đích**: Xử lý hành động của người chơi trong game
- **Workflow**:
  1. Tìm `GameSession` theo gameId
  2. Gọi `session.handlePlayerAction(player, grainIndex)`

##### **endGame(...)**
- **Mục đích**: Kết thúc một phiên game
- **Parameters**: gameId, winner, player1, player2, score1, score2, duration
- **Workflow**:
  1. Xóa session khỏi `activeSessions`
  2. Set `inGame = false` cho cả hai client
  3. Set `currentGameId = null`
  4. Gọi `saveGameResult()` để lưu vào database
  5. Broadcast cập nhật danh sách user

#### **Database Functions:**

##### **authenticateUser(String username, String password)**
- **Mục đích**: Xác thực đăng nhập
- **Return**: boolean (true nếu thông tin đúng)
- **Query**: `SELECT * FROM users WHERE username = ? AND password = ?`

##### **registerUser(String username, String password)**
- **Mục đích**: Đăng ký user mới
- **Return**: boolean (true nếu thành công)
- **Query**: `INSERT INTO users (username, password) VALUES (?, ?)`

##### **getUserTotalScore(String username)**
- **Mục đích**: Lấy tổng điểm của user
- **Return**: int (tổng điểm)
- **Query**: `SELECT total_score FROM users WHERE username = ?`

##### **getLeaderboard()**
- **Mục đích**: Lấy bảng xếp hạng top 20 người chơi
- **Return**: String format `"LEADERBOARD:username1,score1,played1,won1,winrate1;..."`
- **Sort by**: Tỷ lệ thắng DESC, tổng điểm DESC
- **Query**: Tính win_rate = (games_won * 100.0 / games_played)

##### **saveGameResult(...)**
- **Mục đích**: Lưu kết quả game vào database
- **Workflow**:
  1. Insert vào bảng `game_results`
  2. Gọi `updatePlayerStats()` cho cả hai người chơi

##### **updatePlayerStats(String username, boolean won, int score)**
- **Mục đích**: Cập nhật thống kê người chơi
- **Workflow**:
  1. Cộng điểm vào `total_score`
  2. Tăng `games_played` lên 1
  3. Tăng `games_won` lên 1 nếu thắng

---

## 2. FILE CLIENTHANDLER.JAVA

### Mục đích:
Xử lý kết nối và giao tiếp với mỗi client riêng biệt. Mỗi client sẽ có một instance ClientHandler riêng.

### Thuộc tính chính:
```java
private Socket socket;              // Socket kết nối với client
private BufferedReader reader;      // Đọc data từ client
private PrintWriter writer;         // Gửi data cho client
private GameServer server;          // Tham chiếu đến server
private String username;            // Tên người dùng
private boolean inGame;             // Trạng thái đang chơi game
private String currentGameId;       // ID game hiện tại
```

### Workflow chính:

#### 1. Khởi tạo (Constructor):
```
ClientHandler(Socket, GameServer) → Tạo reader/writer từ socket → Set inGame = false
```

#### 2. Main Loop (run()):
```
run() → Đọc message từ client → handleMessage() → Xử lý theo command → Lặp lại
```

### Chi tiết các function:

#### **run()**
- **Mục đích**: Thread chính xử lý tin nhắn từ client
- **Workflow**:
  1. Vòng lặp đọc message từ `reader.readLine()`
  2. Gọi `handleMessage(message)` cho mỗi message
  3. Nếu có lỗi IOException → client disconnect → gọi `cleanup()`

#### **handleMessage(String message)**
- **Mục đích**: Phân tích và xử lý message từ client
- **Format**: `"COMMAND:DATA"`
- **Commands**:
  - `LOGIN:username,password` → `handleLogin()`
  - `REGISTER:username,password` → `handleRegister()`
  - `GET_ONLINE_USERS` → `server.sendOnlineUsersToClient(this)`
  - `INVITE:username` → `server.handleGameInvitation(username, data)`
  - `ACCEPT_INVITATION:username` → `server.handleInvitationResponse(..., true)`
  - `REJECT_INVITATION:username` → `server.handleInvitationResponse(..., false)`
  - `GAME_ACTION:grainIndex` → `handleGameAction()`
  - `GET_LEADERBOARD` → `sendMessage(server.getLeaderboard())`
  - `QUIT_GAME` → `handleQuitGame()`
  - `PRIVATE_MESSAGE:recipient:message` → `server.sendPrivateMessage(username, recipient, message)`

#### **handleLogin(String data)**
- **Mục đích**: Xử lý đăng nhập
- **Workflow**:
  1. Parse `data` thành username và password
  2. Gọi `server.authenticateUser()`
  3. Kiểm tra user đã online chưa
  4. Nếu OK: Set username, gọi `server.addClient()`, gửi `"LOGIN_SUCCESS:username"`
  5. Nếu lỗi: Gửi `"LOGIN_FAILED:reason"`

#### **handleRegister(String data)**
- **Mục đích**: Xử lý đăng ký
- **Workflow**:
  1. Parse data thành username và password
  2. Gọi `server.registerUser()`
  3. Gửi `"REGISTER_SUCCESS"` hoặc `"REGISTER_FAILED:reason"`

#### **handleGameAction(String data)**
- **Mục đích**: Xử lý hành động trong game
- **Workflow**:
  1. Parse data thành grainIndex (int)
  2. Kiểm tra đang trong game (`currentGameId != null`)
  3. Gọi `server.handleGameAction(currentGameId, username, grainIndex)`

#### **handleSendMessage(String data)**
- **Mục đích**: Xử lý gửi tin nhắn riêng tư
- **Workflow**:
  1. Parse data thành recipient và message
  2. Gọi `server.sendPrivateMessage(username, recipient, message)`
  3. Gửi phản hồi thành công hoặc lỗi cho client

#### **handleQuitGame()**
- **Mục đích**: Xử lý người chơi thoát game giữa chừng
- **Workflow**:
  1. Kiểm tra đang trong game
  2. Gửi `"GAME_ENDED:QUIT,0,0"` cho client
  3. Set `inGame = false`, `currentGameId = null`
  4. Gọi `server.broadcastOnlineUsers()`

#### **sendMessage(String message)**
- **Mục đích**: Gửi message cho client
- **Implementation**: `writer.println(message)`

#### **cleanup()**
- **Mục đích**: Dọn dẹp khi client disconnect
- **Workflow**:
  1. Gọi `server.removeClient(username)`
  2. Nếu đang trong game: gọi `handleQuitGame()`
  3. Đóng reader, writer, socket

---

## 3. FILE GAMECLIENT.JAVA

### Mục đích:
Ứng dụng client với giao diện JavaFX, kết nối đến server và hiển thị game.

### Thuộc tính chính:
```java
private Socket socket;                  // Kết nối đến server
private BufferedReader reader;          // Đọc data từ server
private PrintWriter writer;             // Gửi data cho server
private Stage primaryStage;             // Cửa sổ chính JavaFX

// UI Components
private VBox loginPane;                 // Giao diện đăng nhập
private VBox mainGamePane;              // Menu chính
private VBox gamePlayPane;              // Giao diện chơi game

// Game state
private String currentUsername;         // Tên người dùng
private String currentGameId;           // ID game hiện tại
private String opponent;                // Đối thủ
private int currentScore;               // Điểm hiện tại
private int timeRemaining;              // Thời gian còn lại
private Timer gameTimer;                // Timer đếm ngược

// UI Labels
//private Label scoreLabel;               // Hiển thị điểm
//private Label timerLabel;               // Hiển thị thời gian
//private Label opponentScoreLabel;       // Điểm đối thủ
//private GridPane grainGrid;             // Lưới 50 hạt thóc
//private ListView<String> userListView;  // Danh sách user online
```

### Workflow chính:

#### 1. Khởi động ứng dụng:
```
start() → createLoginUI() → connectToServer() → Hiển thị login screen
```

#### 2. Sau khi đăng nhập thành công:
```
Login success → createMainGameUI() → Request online users → Hiển thị menu chính
```

#### 3. Khi bắt đầu game:
```
Game invitation → Accept → createGamePlayUI() → startGameTimer() → Chơi game
```

### Chi tiết các function:

#### **connectToServer()**
- **Mục đích**: Kết nối đến server
- **Workflow**:
  1. Tạo Socket đến `localhost:8888`
  2. Tạo reader/writer từ socket
  3. Tạo thread `handleServerMessages()` để lắng nghe server

#### **createLoginUI()**
- **Mục đích**: Tạo giao diện đăng nhập
- **Components**:
  - TextField cho username
  - PasswordField cho password
  - Button "Đăng nhập" → gửi `"LOGIN:username,password"`
  - Button "Đăng ký" → gửi `"REGISTER:username,password"`

#### **createMainGameUI()**
- **Mục đích**: Tạo menu chính sau khi đăng nhập
- **Components**:
  - Label chào mừng
  - ListView hiển thị user online (double-click để mời chơi)
  - Button "Bảng xếp hạng" → gửi `"GET_LEADERBOARD"`
  - Button "Đăng xuất" → disconnect và thoát

#### **createGamePlayUI()**
- **Mục đích**: Tạo giao diện chơi game
- **Components**:
  - Label thông tin game và đối thủ
  - Label điểm số (mình và đối thủ)
  - Label thời gian đếm ngược
  - GridPane 5x10 = 50 Circle (hạt thóc)
  - Button "Thoát game" với xác nhận

#### **handleServerMessages()**
- **Mục đích**: Thread lắng nghe message từ server
- **Workflow**:
  1. Vòng lặp đọc message từ server
  2. Gọi `Platform.runLater(() → processServerMessage(msg))` để update UI

#### **processServerMessage(String message)**
- **Mục đích**: Xử lý message từ server và cập nhật UI
- **Messages**:
  - `LOGIN_SUCCESS:username` → Chuyển sang main menu
  - `LOGIN_FAILED:reason` → Hiển thị lỗi
  - `REGISTER_SUCCESS/FAILED` → Hiển thị thông báo
  - `ONLINE_USERS:data` → Cập nhật danh sách user
  - `GAME_INVITATION:inviter` → Hiển thị dialog xác nhận
  - `INVITATION_REJECTED:user` → Thông báo từ chối
  - `GAME_STARTED:gameId,opponent,time` → Bắt đầu game
  - `GRAIN_RESULT:index,type,score` → Cập nhật kết quả click
  - `OPPONENT_SCORE:player,score` → Cập nhật điểm đối thủ
  - `GAME_ENDED:winner,score1,score2` → Kết thúc game
  - `LEADERBOARD:data` → Hiển thị bảng xếp hạng
  - `INCOMING_MESSAGE:sender:message` → Hiển thị tin nhắn riêng tư

#### **updateOnlineUsers(String data)**
- **Mục đích**: Cập nhật danh sách user online
- **Format data**: `"username1,score1,status1;username2,score2,status2;..."`
- **Display**: `"username - Điểm: score (status)"`

#### **handleGameInvitation(String inviter)**
- **Mục đích**: Xử lý lời mời chơi game
- **Workflow**:
  1. Hiển thị Alert confirmation
  2. Nếu OK: Gửi `"ACCEPT_INVITATION:inviter"`
  3. Nếu Cancel: Gửi `"REJECT_INVITATION:inviter"`

#### **handleGameStarted(String data)**
- **Mục đích**: Bắt đầu phiên game
- **Workflow**:
  1. Parse gameId, opponent, timeRemaining
  2. Gọi `createGamePlayUI()` và chuyển scene
  3. Gọi `startGameTimer()`

#### **startGameTimer()**
- **Mục đích**: Bắt đầu đếm ngược thời gian
- **Workflow**:
  1. Tạo Timer chạy mỗi 1 giây
  2. Giảm `timeRemaining`
  3. Cập nhật `timerLabel`
  4. Dừng timer khi hết thời gian

#### **handleGrainResult(String data)**
- **Mục đích**: Xử lý kết quả click hạt thóc
- **Data**: `"grainIndex,grainType,newScore"`
- **Workflow**:
  1. Parse data
  2. Tìm Circle tại grainIndex trong gridPane
  3. Đổi màu: GREEN (thóc) hoặc BROWN (trấu)
  4. Cập nhật scoreLabel

#### **handleGameEnded(String data)**
- **Mục đích**: Xử lý kết thúc game
- **Workflow**:
  1. Dừng gameTimer
  2. Parse winner và điểm số
  3. Hiển thị dialog kết quả với các lựa chọn:
     - "Chơi tiếp" → Về menu chính
     - "Xem bảng xếp hạng" → Gửi GET_LEADERBOARD
     - "Menu chính" → Về menu

#### **showLeaderboard(String data)**
- **Mục đích**: Hiển thị bảng xếp hạng
- **Format**: Table với các cột: Rank, Tên, Điểm, Trận, Thắng, Tỷ lệ %

---

## 4. FILE GAMESESSION.JAVA

### Mục đích:
Quản lý logic game cho một phiên chơi giữa 2 người chơi.

### Thuộc tính chính:
```java
private static final int TOTAL_GRAINS = 70;    // Tổng số hạt (thực tế trong code)
private static final int TARGET_RICE = 20;     // Số hạt thóc cần để thắng
private static final int GAME_DURATION = 200;  // Thời gian game (200 giây)

private String gameId;                          // ID phiên game
private String player1, player2;               // Hai người chơi
private GameServer server;                      // Tham chiếu server

private Map<String, Integer> playerScores;      // Điểm của từng người
private Map<String, Set<Integer>> playerClicks; // Các hạt đã click
private boolean[] grainTypes;                   // true=thóc, false=trấu
private Timer gameTimer;                        // Timer game
private long gameStartTime;                     // Thời điểm bắt đầu
private boolean gameEnded;                      // Trạng thái game
```

### Workflow chính:

#### 1. Khởi tạo session:
```
GameSession() → initializeGrains() → Set player scores = 0
```

#### 2. Bắt đầu game:
```
startGame() → Gửi GAME_STARTED cho cả 2 client → startGameTimer()
```

#### 3. Xử lý click:
```
handlePlayerAction() → Kiểm tra valid → Cập nhật điểm → Kiểm tra win condition
```

### Chi tiết các function:

#### **initializeGrains()**
- **Mục đích**: Khởi tạo 70 hạt với vị trí ngẫu nhiên
- **Workflow**:
  1. Tạo mảng `grainTypes[70]` với giá trị false
  2. Random chọn `TARGET_RICE + 5` vị trí làm hạt thóc (true)
  3. Đảm bảo có đủ hạt thóc để thắng game

#### **startGame()**
- **Mục đích**: Bắt đầu phiên game
- **Workflow**:
  1. Lưu thời gian bắt đầu
  2. Lấy ClientHandler của cả 2 người chợi
  3. Gửi `"GAME_STARTED:gameId,opponent,duration"` cho mỗi người
  4. Gọi `startGameTimer()`

#### **startGameTimer()**
- **Mục đích**: Tạo timer tự động kết thúc game
- **Workflow**:
  1. Tạo Timer
  2. Schedule task sau `GAME_DURATION = 200` giây
  3. Khi hết thời gian: gọi `endGameByTimeout()`

#### **handlePlayerAction(String player, int grainIndex)**
- **Mục đích**: Xử lý hành động click hạt của người chợi
- **Workflow**:
  1. Kiểm tra game chưa kết thúc và grainIndex hợp lệ (0-69)
  2. Kiểm tra người chợi chưa click hạt này
  3. Thêm grainIndex vào `playerClicks[player]`
  4. Kiểm tra `grainTypes[grainIndex]`:
     - Nếu là thóc (true):
       - Tăng điểm người chợi
       - Gửi `"GRAIN_RESULT:index,RICE,newScore"` cho người chợi
       - Gửi `"OPPONENT_SCORE:player,score"` cho đối thủ
       - Kiểm tra đạt `TARGET_RICE = 20` → thắng game
     - Nếu là trấu (false):
       - Gửi `"GRAIN_RESULT:index,CHAFF,currentScore"`

#### **endGameByTimeout()**
- **Mục đích**: Kết thúc game khi hết thời gian
- **Workflow**:
  1. So sánh điểm của 2 người chơi
  2. Xác định winner: player có điểm cao hơn, hoặc "DRAW" nếu bằng nhau
  3. Gọi `endGame(winner)`

#### **endGame(String winner)**
- **Mục đích**: Kết thúc game và thông báo kết quả
- **Workflow**:
  1. Set `gameEnded = true`
  2. Cancel gameTimer
  3. Tính thời gian chơi
  4. Gửi `"GAME_ENDED:winner,score1,score2"` cho cả 2 client
  5. Gọi `server.endGame()` để lưu kết quả và cleanup

---

## 5. FILE CHATWINDOW.JAVA

### Mục đích:
Tạo cửa sổ chat riêng tư giữa hai người chợi.

### Thuộc tính chính:
```java
private TextArea chatArea;      // Hiển thị tin nhắn
private TextField messageField; // Nhập tin nhắn
```

### Workflow chính:
```
ChatWindow(currentUser, recipient, messageSender) → Tạo UI → Xử lý send message
```

### Chi tiết các function:

#### **Constructor(String currentUser, String recipient, Consumer<String> messageSender)**
- **Mục đích**: Khởi tạo cửa sổ chat
- **Workflow**:
  1. Tạo TextArea không thể edit để hiển thị tin nhắn
  2. Tạo TextField để nhập tin nhắn
  3. Tạo Button "Gửi"
  4. Khi gửi tin nhắn: gọi messageSender.accept() và hiển thị trong chatArea

#### **appendMessage(String message)**
- **Mục đích**: Thêm tin nhắn vào khu vực hiển thị
- **Implementation**: `chatArea.appendText(message + "\n")`

---

## 6. MÂU THUẪN TRONG CODE CẦN SỬA

### Vấn đề hiện tại:
1. **GameSession**: `TOTAL_GRAINS = 70`, `GAME_DURATION = 200` giây
2. **GameClient**: Tạo grid 5x10 = 50 Circle, timer countdown từ 15 giây
3. **Documentation cũ**: Ghi 50 grains, 15 giây

### Khuyến nghị sửa:
1. **Thống nhất số lượng hạt**: Chọn 50 hoặc 70 và cập nhật cả server/client
2. **Thống nhất thời gian**: Chọn 15 giây (phù hợp với game nhanh) hoặc 200 giây
3. **Cập nhật UI**: Nếu dùng 70 grains thì cần tạo grid 7x10 hoặc 5x14

---

## FLOW WORK TỔNG THỂ (CẬP NHẬT)

### 1. Khởi động hệ thống:
```
1. Khởi động GameServer
   ├── Khởi tạo database (tạo bảng users, game_results)
   ├── Tạo ServerSocket port 8888
   └── Lắng nghe kết nối client

2. Client khởi động
   ├── Tạo giao diện đăng nhập JavaFX với CSS styling
   ├── Kết nối Socket đến server localhost:8888
   └── Bắt đầu thread lắng nghe server
```

### 2. Đăng nhập/Đăng ký:
```
Client gửi LOGIN/REGISTER → Server xác thực database → Phản hồi SUCCESS/FAILED
                                                     ↓
                                          Server thêm vào onlineClients
                                                     ↓
                                          Broadcast danh sách user online
                                                     ↓
                                          Client chuyển sang main menu với user list
```

### 3. Mời chợi game:
```
Client A click chuột phải trên Client B → Chọn "Mời chợi" → Gửi INVITE:B
                                                                    ↓
                                Server gửi GAME_INVITATION:A cho B
                                                                    ↓
                                B chọn Accept/Reject → Server nhận phản hồi
                                                                    ↓
                                      Nếu Accept: Server tạo GameSession mới
                                                                    ↓
                                      Gửi GAME_STARTED cho cả A và B
```

### 4. Chat riêng tư:
```
Client A click chuột phải trên Client B → Chọn "Nhắn tin" → Mở ChatWindow
                                                                    ↓
                        Nhập tin nhắn và gửi → PRIVATE_MESSAGE:B:content
                                                                    ↓
                                Server gửi INCOMING_MESSAGE:A:content cho B
                                                                    ↓
                                        B nhận và hiển thị trong ChatWindow
```

### 5. Chợi game:
```
1. Client nhận GAME_STARTED
   ├── Chuyển sang giao diện game
   ├── Hiển thị lưới 50 Circle (mâu thuẫn với server 70 grains)
   └── Bắt đầu đếm ngược (client: 15s, server: 200s)

2. Client click hạt thóc
   ├── Gửi GAME_ACTION:grainIndex (0-49 từ client UI)
   ├── Server chuyển cho GameSession
   ├── GameSession kiểm tra loại hạt
   ├── Cập nhật điểm nếu là thóc
   ├── Gửi GRAIN_RESULT cho người click
   ├── Gửi OPPONENT_SCORE cho đối thủ
   └── Kiểm tra điều kiện thắng (20 hạt thóc)

3. Kết thúc game (hết thời gian hoặc đạt 20 điểm)
   ├── GameSession gửi GAME_ENDED
   ├── Client hiển thị dialog kết quả với options
   ├── Server lưu vào database
   ├── Cập nhật thống kê người chợi
   └── Client về menu chính
```

### 6. Các tính năng phụ:
```
- Xem bảng xếp hạng: GET_LEADERBOARD → Server query database → Hiển thị top 20
- Thoát game giữa chừng: QUIT_GAME → Tự động thua → Về menu chính  
- Chat riêng tư: PRIVATE_MESSAGE → Hiển thị trong ChatWindow
- Disconnect: Client tự động cleanup → Server remove khỏi online list
- Context menu: Click chuột phải trên user list → Mời chợi hoặc nhắn tin
```

## PROTOCOL GIAO TIẾP (CẬP NHẬT)

### Messages từ Client → Server:
- `LOGIN:username,password`
- `REGISTER:username,password`
- `GET_ONLINE_USERS`
- `INVITE:username`
- `ACCEPT_INVITATION:username`
- `REJECT_INVITATION:username`
- `GAME_ACTION:grainIndex` (0-49 từ client UI)
- `GET_LEADERBOARD`
- `QUIT_GAME`
- `PRIVATE_MESSAGE:recipient:message`

### Messages từ Server → Client:
- `LOGIN_SUCCESS:username` / `LOGIN_FAILED:reason`
- `REGISTER_SUCCESS` / `REGISTER_FAILED:reason`
- `ONLINE_USERS:user1,score1,status1;user2,score2,status2;...`
- `GAME_INVITATION:inviter`
- `INVITATION_REJECTED:username`
- `GAME_STARTED:gameId,opponent,duration`
- `GRAIN_RESULT:grainIndex,type,newScore`
- `OPPONENT_SCORE:player,score`
- `GAME_ENDED:winner,score1,score2`
- `LEADERBOARD:user1,score1,played1,won1,rate1;...`
- `INCOMING_MESSAGE:sender:message`
- `SYSTEM_MESSAGE:content`

## YÊU CẦU HỆ THỐNG

### Phần mềm cần thiết:
- Java 18+ với JavaFX (theo pom.xml)
- MySQL Server 8.0+
- Maven 3.6+ (để build project)

### Dependencies chính (từ pom.xml):
- JavaFX Controls, FXML, Web, Swing, Media (18.0.2)
- MySQL JDBC Driver (qua java.sql module)
- ControlsFX, FormsFX, ValidatorFX
- FXGL game library

### Database setup:
```sql
CREATE DATABASE rice_game CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Server sẽ tự động tạo các bảng khi khởi động
```

### Cách chợi:
1. Khởi động MySQL Server
2. Cấu hình database connection trong GameServer (DB_USER, DB_PASSWORD)
3. Chạy `GameServer.main()` để khởi động server
4. Chạy `GameClient.main()` cho mỗi client
5. Hoặc dùng Maven: `mvn clean javafx:run`

### Cấu hình hiện tại:
- Server port: 8888
- Database URL: localhost:3306/rice_game
- Database user: root, password: 123456
- **Game duration**: 200 giây (server) vs 15 giây (client UI) - CẦN SỬA
- **Target rice**: 20 hạt thóc để thắng
- **Total grains**: 70 (server) vs 50 (client UI) - CẦN SỬA

### Styling:
- CSS file: `src/main/resources/com/example/gamesocket/styles/styles.css`
- Dark theme với màu chủ đạo: #2D3436, #636E72, #0984E3
- Font: Segoe UI, Arial
- Responsive buttons và form elements
