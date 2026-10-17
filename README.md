# MicroEmulator (fork — hỗ trợ nhiều tab)

Đây là bản fork của [barteo/microemu](https://github.com/barteo/microemu) (J2ME device emulator viết bằng Java thuần, chạy trên J2SE).

Bản fork bổ sung **thanh tab kiểu Chrome** để chạy **nhiều game J2ME cùng lúc**, mỗi tab là một phiên (session) độc lập. Phần lõi (CLDC/MIDP) hầu như giữ nguyên so với bản gốc.

## Thay đổi so với bản gốc

### 1. Chạy nhiều game đồng thời trên nhiều tab

- Cửa sổ chính giờ là một `JFrame` (`org.microemu.app.multitab.MultiMain`) quản lý thanh tab và vùng nội dung.
- `org.microemu.app.Main` trước đây `extends JFrame`, nay chuyển thành `extends JPanel` và `implements EmulatorSession` — mỗi tab chứa một instance `Main` riêng, cùng menu bar, vùng hiển thị, và nút Resize của nó.
- Thao tác tab: chuyển tab, nút `+` thêm tab mới, nút `x` đóng tab.
- Mở MIDlet (File / URL / danh sách gần đây / kéo-thả) sẽ nạp vào **tab đang hoạt động**.
- Nút `+` nằm ngay cạnh tab cuối (không phải ở sát mép phải).
- Khi số tab vượt quá bề rộng cửa sổ, dải tab tự cuộn; có thêm nút `<` và `>` ở phía phải để điều hướng/chuyển tab (không cần kéo rộng cửa sổ). Hỗ trợ cả cuộn bằng con lăn chuột; tab đang hoạt động luôn được tự cuộn vào tầm nhìn.
- Tiêu đề tab cập nhật **ngay** khi mở game trong tab hiện tại (không phải đợi mở tab mới).

### 2. Cách ly phiên bằng ClassLoader

- `org.microemu.app.multitab.SessionClassLoader`: mỗi tab có một classloader riêng, nạp từ các URL trong `java.class.path`.
  - **Child-first** với `org.microemu.*` và `javax.microedition.*` → mỗi tab giữ trạng thái tĩnh (static) riêng, không giẫm lên nhau.
  - **Parent-first** với `java.*`, `javax.swing.*` và package host `org.microemu.app.multitab.` → giữ an toàn cho các lớp nền tảng và lớp host.
- Nhờ vậy, các `static` của lõi không bị chia sẻ/ô nhiễm giữa các tab.

### 3. Cấu hình thiết bị dùng chung

- Cấu hình thiết bị (device) được **chia sẻ giữa các tab**; thay đổi device ở một tab được truyền (callback `Runnable`) sang các tab còn lại qua `MultiMain`.
- Nút **Resize** hoạt động thật sự (gọi `pack()` lại cửa sổ theo kích thước thiết bị).

### 4. Giao diện & tiện ích

- `org.microemu.app.multitab.EmulatorSession`: interface giao tiếp host ↔ session, chỉ dùng kiểu JDK.
- `org.microemu.app.Common.setMidletOpenedListener(Runnable)`: sự kiện khi một MIDlet được mở, dùng để cập nhật tiêu đề tab.
- `Main.main` uỷ quyền cho `MultiMain.main`; nhánh `--headless` vẫn được giữ nguyên.

### 5. Sửa lỗi build & cấu hình

- Sửa cấu hình `pom.xml` các module (`api/*`, `microemu-cldc`, `microemu-midp`, `microemu-javase-applet`, ...) và `proguard.conf` để build được với toolchain hiện tại.
- `Config` **không còn lưu/khôi phục kích thước cửa sổ** (window geometry) — tránh việc vị trí/kích thước cũ ghi đè kích thước thiết bị.

### 6. Sửa nhập liệu bàn phím (ký tự `@`, ô email)

- `J2SEInputMethod.fireInputMethodListener`: ký tự gõ từ bàn phím PC (ví dụ `@` = Shift+2) nay được chèn trực tiếp, chỉ lọc theo ràng buộc `TextField`. Trước đây ký tự chỉ được chèn nếu nó thuộc bộ ký tự của đúng phím điện thoại mà phím PC ánh xạ tới, nên `@` (nằm ở phím "1" nhưng Shift+2 lại ánh xạ phím "2") bị loại bỏ.
- `InputMethodImpl.filterConstraints`: bổ sung xử lý ràng buộc `TextField.EMAILADDR` (trước đây là `// TODO` → lọc bỏ mọi ký tự, không gõ được gì vào ô email).

### 7. Cửa sổ Log console — chọn và copy được

- `LogTextArea`: `setEditable(false)` khiến `JTextComponent` mặc định **không focusable**, nên không chọn/Ctrl+C được. Nay gọi `setFocusable(true)` để chọn và copy log.
- Thêm **Copy** / **Select All**: menu chuột phải trong vùng log và menu "Log" của cửa sổ Log console.
- `LogTextArea.append`: khi người dùng đang có vùng chọn (để copy), không tự nhảy caret về cuối/mất vùng chọn nữa — nhờ vậy vẫn copy được trong lúc log đang chạy.



## Cấu trúc file chính

| File | Vai trò |
|---|---|
| `microemulator/microemu-javase-swing/src/main/java/org/microemu/app/multitab/MultiMain.java` | Host `JFrame`: thanh tab cuộn được, nút `+`/`x`/`<`/`>`/Resize, truyền device, xử lý thoát |
| `.../multitab/SessionClassLoader.java` | ClassLoader cách ly theo phiên, đọc URL từ `java.class.path` |
| `.../multitab/EmulatorSession.java` | Interface host ↔ session (chỉ kiểu JDK) |
| `microemulator/microemu-javase-swing/src/main/java/org/microemu/app/Main.java` | Session (`JPanel`): menu bar, nội dung, resize, cập nhật tiêu đề |
| `microemulator/microemu-javase/src/main/java/org/microemu/app/Common.java` | Thêm `midletOpenedListener` |

## Build

Yêu cầu: JDK 8 (`JAVA_HOME`) và Maven.

```sh
cd microemulator
mvn -o "-Dmaven.test.skip=true" package
```

Jar tạo ra: `microemulator/microemulator/target/microemulator-3.0.0-SNAPSHOT.jar` (Main-Class `org.microemu.app.Main`).

## Chạy

```sh
java -jar microemulator/microemulator/target/microemulator-3.0.0-SNAPSHOT.jar [game.jad]
```

### Đặt RAM (heap)

Heap phải được truyền lúc khởi động JVM, không đổi được khi app đang chạy. Dùng một file launcher (ví dụ `run2.bat`):

```bat
start "" javaw -Xmx1024m -jar microemulator\microemulator\target\microemulator-3.0.0-SNAPSHOT.jar %*
```

Chỉnh `-Xmx1024m` để thay đổi RAM tối đa. Có thể set chung bằng biến môi trường `JAVA_TOOL_OPTIONS=-Xmx1024m`.

## Ghi chú về dữ liệu game (RecordStore)

MicroEmulator 3.x dùng định dạng file record store **mới** (`*.rms`, magic `MIDRMS`) khác hoàn toàn với bản 2.x (`*.rs`) — đây là thay đổi cố ý từ upstream. Vì vậy dữ liệu lưu bởi bản 2.x sẽ **không** được bản 3.x đọc lại. Dữ liệu của bản này nằm ở:

```
~/.microemulator/suite-<MIDlet-Name>/
```

(theo `System.getProperty("user.home")`).
