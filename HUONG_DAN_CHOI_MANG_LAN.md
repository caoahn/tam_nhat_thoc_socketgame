# HƯỚNG DẪN CHƠI GAME QUA MẠNG LAN

## 📌 TỔNG QUAN

Sau khi sửa code, game của bạn đã hỗ trợ chơi qua mạng LAN (Local Area Network). Người khác trong cùng mạng WiFi/LAN với bạn có thể kết nối và chơi cùng!

---

## 🖥️ PHẦN 1: SETUP SERVER (NGƯỜI TỔ CHỨC GAME)

### Bước 1: Tìm địa chỉ IP của máy tính bạn

#### Trên Windows:
1. Mở **Command Prompt** (Cmd)
   - Nhấn `Win + R`
   - Gõ `cmd` và Enter

2. Gõ lệnh:
   ```
   ipconfig
   ```

3. Tìm dòng **IPv4 Address** trong phần **Wireless LAN adapter Wi-Fi** hoặc **Ethernet adapter**
   
   Ví dụ:
   ```
   IPv4 Address. . . . . . . . . . . : 192.168.1.105
   ```
   
   **➡️ Địa chỉ IP của bạn là: `192.168.1.105`**

### Bước 2: Tắt Firewall hoặc cho phép port 8888

#### Cách 1: Tắt Firewall tạm thời (dễ nhất)
1. Mở **Windows Defender Firewall**
2. Click **Turn Windows Defender Firewall on or off**
3. Chọn **Turn off** cho cả Private và Public networks
4. Click OK

⚠️ **Lưu ý**: Nhớ bật lại sau khi chơi xong!

#### Cách 2: Mở port 8888 (khuyến nghị)
1. Mở **Windows Defender Firewall with Advanced Security**
2. Click **Inbound Rules** > **New Rule**
3. Chọn **Port** > Next
4. Chọn **TCP**, nhập port: `8888` > Next
5. Chọn **Allow the connection** > Next
6. Đặt tên: "Rice Game Server" > Finish

### Bước 3: Chạy GameServer
1. Đảm bảo MySQL đang chạy
2. Chạy class `GameServer.java`
   ```
   Hoặc dùng IDE: Run GameServer.main()
   ```
3. Nếu thấy dòng này là thành công:
   ```
   Game Server started on port 8888
   Database initialized successfully
   ```

### Bước 4: Chạy GameClient (người tổ chức cũng chơi)
1. Chạy class `GameClient.java`
2. Trong giao diện đăng nhập:
   - **Địa chỉ Server**: Nhập `localhost` hoặc IP máy bạn (ví dụ: `192.168.1.105`)
   - Nhập username và password
   - Đăng nhập

### Bước 5: Chia sẻ thông tin cho bạn bè
**Gửi cho họ:**
- Địa chỉ IP của bạn: `192.168.1.105` (thay bằng IP thật của bạn)
- Port: `8888`
- Họ cần kết nối cùng mạng WiFi/LAN với bạn

---

## 👥 PHẦN 2: SETUP CLIENT (NGƯỜI THAM GIA CHƠI)

### Yêu cầu:
- ✅ Kết nối cùng mạng WiFi/LAN với người tổ chức
- ✅ Có file `GameClient.java` (hoặc file JAR đã build)
- ✅ Đã cài Java 18+

### Các bước:

1. **Kết nối cùng mạng WiFi**
   - Kết nối vào cùng WiFi với người tổ chức game
   - Ví dụ: Cùng WiFi tên "MyHome_WiFi"

2. **Chạy GameClient**
   - Chạy class `GameClient.java`

3. **Nhập thông tin kết nối**
   - **Địa chỉ Server**: Nhập IP của người tổ chức (ví dụ: `192.168.1.105`)
   - **Username**: Tên đăng nhập của bạn
   - **Password**: Mật khẩu của bạn
   - Click **Đăng nhập** (hoặc **Đăng ký** nếu chưa có tài khoản)

4. **Bắt đầu chơi!**
   - Sau khi đăng nhập, bạn sẽ thấy danh sách người chơi online
   - Click chuột phải vào tên người chơi > Chọn "Mời chơi"

---

## 🔧 XỬ LÝ SỰ CỐ

### Lỗi "Không thể kết nối đến server"

**Nguyên nhân và cách khắc phục:**

1. **Kiểm tra IP có đúng không?**
   - Đảm bảo client nhập đúng IP của server
   - Gõ lại lệnh `ipconfig` để xác nhận IP

2. **Server đã chạy chưa?**
   - Kiểm tra GameServer có đang chạy không
   - Xem console có dòng "Game Server started on port 8888" không

3. **Firewall có chặn không?**
   - Tắt Firewall tạm thời để test
   - Hoặc mở port 8888 như hướng dẫn ở trên

4. **Có cùng mạng không?**
   - Kiểm tra cả hai máy cùng WiFi/LAN
   - Không dùng 4G/5G mobile data
   - Ping thử:
     ```
     ping 192.168.1.105
     ```
     Nếu không ping được = không cùng mạng

5. **Port 8888 có bị chiếm không?**
   - Kiểm tra port:
     ```
     netstat -ano | findstr :8888
     ```
   - Nếu có chương trình khác dùng port 8888, đổi port trong code

---

## 🌐 CHƠI QUA INTERNET (NÂNG CAO)

Nếu muốn chơi qua Internet (không cùng mạng LAN), bạn cần:

### Cách 1: Port Forwarding (trên Router)
1. Truy cập trang quản trị Router (thường là `192.168.1.1`)
2. Tìm mục **Port Forwarding** hoặc **Virtual Server**
3. Thêm rule:
   - **External Port**: 8888
   - **Internal Port**: 8888
   - **Internal IP**: IP máy tính bạn (ví dụ: `192.168.1.105`)
   - **Protocol**: TCP
4. Lấy **Public IP** của bạn (Google: "what is my ip")
5. Chia sẻ Public IP cho bạn bè

⚠️ **Rủi ro bảo mật**: Không khuyến khích cho người mới!

### Cách 2: Dùng Hamachi/Radmin VPN (đơn giản hơn)
1. Tải **Hamachi** hoặc **Radmin VPN** (miễn phí)
2. Tạo mạng ảo và mời bạn bè vào
3. Dùng IP ảo từ Hamachi thay vì IP thật
4. Chơi như thể cùng mạng LAN

### Cách 3: Dùng Ngrok (cho developer)
```bash
ngrok tcp 8888
```
Ngrok sẽ tạo địa chỉ công khai, ví dụ: `tcp://0.tcp.ngrok.io:12345`

---

## 📊 KIỂM TRA KẾT NỐI

### Trên Server (người tổ chức):
- Mở GameServer console
- Khi client kết nối thành công, sẽ thấy:
  ```
  User [username] connected. Online users: 2
  ```

### Trên Client (người chơi):
- Sau khi đăng nhập thành công
- Sẽ thấy danh sách người chơi online
- Có thể mời chơi hoặc chat

---

## ✅ CHECKLIST TRƯỚC KHI BẮT ĐẦU

### Server (người tổ chức):
- [ ] MySQL đang chạy
- [ ] Đã tìm được IP của máy (ví dụ: `192.168.1.105`)
- [ ] Firewall đã tắt hoặc đã mở port 8888
- [ ] GameServer đang chạy (thấy "Game Server started...")
- [ ] Đã chia sẻ IP cho bạn bè

### Client (người tham gia):
- [ ] Đã kết nối cùng WiFi với người tổ chức
- [ ] Đã có IP của server
- [ ] GameClient đã chạy
- [ ] Đã nhập đúng IP server vào ô "Địa chỉ Server"

---

## 💡 MẸO HAY

1. **Test ngay trên máy server trước:**
   - Chạy cả Server và Client trên cùng 1 máy
   - Dùng IP `localhost` để test
   - Đảm bảo mọi thứ hoạt động trước khi gọi bạn bè

2. **Dùng IP tĩnh (Static IP) cho máy server:**
   - Vào Router settings
   - Đặt DHCP Reservation cho MAC address máy server
   - IP sẽ không đổi mỗi lần khởi động lại

3. **Tạo file BAT để chạy nhanh:**
   ```batch
   @echo off
   echo Starting Rice Game Server...
   java -cp target/classes com.example.gamesocket.GameServer
   pause
   ```

4. **Build thành file JAR để chia sẻ:**
   ```
   mvn clean package
   ```
   File JAR sẽ ở trong folder `target/`
   Bạn bè chỉ cần file JAR + Java, không cần code

---

## 🎮 VUI CHƠI VUI VẺ!

Chúc bạn có những trận game vui vẻ cùng bạn bè! 🌾🎉

Nếu gặp vấn đề, hãy kiểm tra lại từng bước trong hướng dẫn này.

