# 1. Giới thiệu dự án

- **Fimory** là hệ thống web quản lý và phân phối **phim + truyện** (truyện chữ và truyện tranh), gồm backend Java Spring Boot, frontend React/Vite và cơ sở dữ liệu SQL Server.
- Dự án hỗ trợ các luồng thực tế:
  - Người dùng đăng ký/đăng nhập, xem phim, đọc truyện, lưu lịch sử, yêu thích, bình luận, đánh giá.
  - Người đăng nội dung (Uploader) upload phim/truyện/chapter.
  - Admin kiểm duyệt nội dung, quản lý user, role upgrade, thống kê, bình luận.
- Hệ thống còn có tích hợp chatbot Gemini, upload file media, crawl truyện/chapter và module video HLS streaming.

# 2. Công nghệ sử dụng

- **Backend**
  - Java 21
  - Spring Boot 3.3.5
  - Spring Web, Spring Security, Spring Data JPA, JdbcTemplate
  - SQL Server JDBC Driver (`mssql-jdbc`)
- **Frontend**
  - React 18 + TypeScript
  - Vite 5
  - TailwindCSS
  - React Router, React Hook Form, Framer Motion
- **Database**
  - Microsoft SQL Server
- **Công nghệ / dịch vụ khác**
  - Gemini API (chatbot)
  - Cơ chế email qua biến môi trường `EMAIL_USER`, `EMAIL_PASSWORD` (hiện tại một số endpoint email vẫn để TODO)
  - Upload file vào thư mục local `uploads` và public qua `/storage/**`
  - Crawl truyện/chapter từ website VN (đã có xử lý thực tế trong service crawl truyện)
  - Video module: upload video, convert HLS bằng FFmpeg/FFprobe, stream có token

# 3. Kiến trúc hệ thống

- **Backend làm gì**
  - Cung cấp REST API dưới prefix `/api` (trừ health endpoint).
  - Xử lý nghiệp vụ: auth, phim, truyện, chapter, bình luận, đánh giá, yêu thích, lịch sử, thông báo, admin, upload, crawl, video.
  - Kết nối SQL Server qua cấu hình `spring.datasource.*` trong `application.yml`.
- **Frontend làm gì**
  - Render giao diện người dùng và trang quản trị bằng React.
  - Gọi API qua `fetch` đến `/api/...`.
  - Dev mode dùng Vite proxy (`/api`, `/storage`) trỏ về `http://localhost:4000`.
- **Database làm gì**
  - Lưu toàn bộ dữ liệu người dùng, phân quyền, phim, truyện, chapter, lịch sử tương tác, thông báo, thống kê, cấu hình hệ thống.
  - Lưu metadata cho các tính năng mở rộng như crawl, truyện tranh, video/server nguồn phát.
- **Cách các phần giao tiếp với nhau**
  - Người dùng thao tác trên frontend.
  - Frontend gửi request HTTP tới backend API.
  - Backend xác thực ngữ cảnh user (qua header `x-user-email` trong giai đoạn hiện tại), xử lý logic, truy vấn SQL Server.
  - Backend trả JSON về frontend để hiển thị.
  - Tài nguyên upload (ảnh/video file) được phục vụ qua `/storage/**`.

# 4. Chức năng hệ thống

- **Nhóm người dùng chung**
  - Đăng ký, đăng nhập, xem thông tin profile.
  - Xem danh sách phim/truyện, xem chi tiết, xem tập/chapter.
  - Tìm kiếm nội dung (`/api/search`, `/api/movies/search`).
  - Bình luận phim/truyện, đánh giá sao, yêu thích.
  - Lưu lịch sử xem phim/đọc truyện.
  - Nhận và quản lý thông báo.
  - Gửi yêu cầu nâng quyền.
- **Nhóm uploader / quản trị nội dung**
  - Upload phim, tập phim (multipart).
  - Upload truyện, chapter; hỗ trợ truyện chữ/truyện tranh.
  - Chỉnh sửa/xóa nội dung đã upload (theo quyền sở hữu hoặc quyền admin/uploader).
- **Nhóm admin**
  - Quản lý phim/truyện/category/user/comment.
  - Duyệt nội dung, đổi role user, khóa/mở user.
  - Duyệt/từ chối role upgrade request.
  - Xem thống kê nội dung và tương tác.
- **Module crawl**
  - Crawl thông tin truyện từ URL, crawl ảnh chapter, dọn file tạm crawl.
  - Endpoint crawl phim hiện có route nhưng một phần logic còn TODO.
- **Module video**
  - Upload video.
  - Xử lý bất đồng bộ sang HLS (720p/480p).
  - Cấp token stream và phát segment theo token.
- **Lưu ý trạng thái chức năng**
  - Một số endpoint tồn tại để giữ contract API nhưng body vẫn trả `TODO` (ví dụ quên mật khẩu/reset password/send OTP/verify OTP và một số crawl phim).

# 5. Cấu trúc thư mục

- `src/main/java/com/fimory/api`
  - Mã nguồn backend Spring Boot (controller/service/repository/security/config).
- `src/main/resources/application.yml`
  - Cấu hình backend: port, datasource SQL Server, upload, video, tích hợp Gemini/Email.
- `web/`
  - Frontend React + Vite.
- `web/src/pages`
  - Các trang giao diện (home, movies, stories, admin, auth...).
- `web/src/lib`
  - Cấu hình API base URL, helper gọi API.
- `web/*.sql` và `web/*.txt`
  - Script khởi tạo/mở rộng dữ liệu SQL Server.
- `uploads/`
  - File upload runtime (cover, poster, chapter images...), được map ra `/storage/**`.
- `video-storage/` (tạo khi chạy video module)
  - Lưu file gốc và HLS output.

# 6. Hướng dẫn cài đặt và chạy

## 6.1 Yêu cầu môi trường

- JDK 21 (bắt buộc theo `pom.xml`).
- SQL Server (SQL Server Express/Developer đều được).
- Maven (hoặc dùng Maven Wrapper `mvnw.cmd` có sẵn).
- Node.js + npm (khuyến nghị Node.js 18+ cho frontend Vite).
- Khuyến nghị công cụ chạy SQL: **SQL Server Management Studio (SSMS)**.

## 6.2 Tạo và khởi tạo cơ sở dữ liệu

- **Mục tiêu**: tạo DB Fimory, tạo schema nền, thêm mở rộng cho truyện/phim crawl, sau đó kiểm tra dữ liệu trùng.
- **Thứ tự chạy bắt buộc**:
  1. `web/cơ sở dữ liệu SQL Server cho hệ thống Fimory - Web Phim & Truyện.txt`
  2. `web/add_story_type_support.sql`
  3. `web/add_movie_crawl_support.sql`
  4. `web/merge_duplicate_movies.sql`

- **Vì sao phải chạy theo thứ tự này**
  - File (1) tạo các bảng gốc (`Users`, `Movies`, `Series`, `Chapters`, ...).
  - File (2) cần bảng `Series`, `Chapters` đã có để thêm `StoryType`, `ChapterImages`, `ImageCount`.
  - File (3) cần bảng `Movies`, `MovieEpisodes` đã có để thêm cột crawl và bảng `MovieServers`.
  - File (4) đọc dữ liệu phim hiện có để kiểm tra trùng lặp; chạy trước khi có dữ liệu nền sẽ không có ý nghĩa.

- **Hướng dẫn chi tiết bằng SSMS**
  1. Mở SSMS, kết nối đúng SQL Server instance.
  2. Tạo database (nếu chưa có):
     - Có thể chạy:
       ```sql
       IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'Fimory')
       BEGIN
           CREATE DATABASE Fimory;
       END
       ```
  3. Mở file SQL (1), đảm bảo đang chạy trên DB `Fimory`.
  4. Chạy file SQL (2) rồi (3) theo đúng thứ tự.
  5. Chạy file SQL (4) để kiểm tra dữ liệu phim có thể trùng.

- **Giải thích từng file**
  - `cơ sở dữ liệu SQL Server cho hệ thống Fimory - Web Phim & Truyện.txt`
    - Là file nền tảng: tạo DB + rất nhiều bảng lõi + seed dữ liệu mẫu + index tối ưu.
    - Có định nghĩa quan hệ FK, unique, default cho phần lớn module hệ thống.
    - Trong file này có kèm thêm một số đoạn debug/mẫu ở cuối (ví dụ placeholder `${user.UserID}`) không phù hợp chạy trực tiếp trong SQL Server.
  - `add_story_type_support.sql`
    - Mở rộng truyện chữ/truyện tranh:
      - Thêm cột `Series.StoryType` (mặc định `Text`).
      - Tạo bảng `ChapterImages` cho truyện tranh.
      - Thêm `Chapters.ImageCount` và đồng bộ dữ liệu.
    - Script có tính idempotent (kiểm tra tồn tại trước khi tạo/thêm).
  - `add_movie_crawl_support.sql`
    - Mở rộng phục vụ crawl/import phim:
      - Thêm các cột metadata crawl cho `MovieEpisodes` và `Movies`.
      - Tạo bảng `MovieServers` để lưu nhiều server video cho 1 tập.
      - Điều chỉnh unique constraint tập phim theo `MovieID + SeasonNumber + EpisodeNumber`.
    - Cũng có tính idempotent.
  - `merge_duplicate_movies.sql`
    - Mục tiêu: hỗ trợ xử lý dữ liệu phim trùng bằng cách liệt kê các cặp phim có khả năng trùng và thống kê tập phim.
    - File hiện tại là script **kiểm tra/phân tích** (SELECT), không tự động merge/xóa dữ liệu.

- **Cách kiểm tra tạo DB thành công**
  - Chạy:
    ```sql
    USE Fimory;
    SELECT COUNT(*) AS TotalTables
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_TYPE = 'BASE TABLE';
    ```
  - Kiểm tra nhanh bảng quan trọng:
    ```sql
    SELECT TOP 5 * FROM Roles;
    SELECT TOP 5 * FROM Users;
    SELECT TOP 5 * FROM Movies;
    SELECT TOP 5 * FROM Series;
    ```

- **Nếu chạy sai thứ tự có thể gặp gì**
  - Lỗi `Invalid object name` do bảng gốc chưa tạo mà đã chạy file mở rộng.
  - Lỗi constraint/index do cấu trúc phụ thuộc chưa tồn tại.
  - Kết quả kiểm tra phim trùng không có ý nghĩa nếu chưa có dữ liệu phim.

## 6.3 Cấu hình biến môi trường backend

Chạy trong **PowerShell**, tại **thư mục gốc backend** (`JavaWebFimory`):

```powershell
$env:SQL_URL="jdbc:sqlserver://<TEN_SERVER>\<TEN_INSTANCE>;databaseName=<TEN_DATABASE>;encrypt=false;trustServerCertificate=true"
$env:SQL_USER="<TEN_USER_SQL>"
$env:SQL_PASSWORD="<MAT_KHAU_SQL>"
$env:GEMINI_API_KEY="AIzaSyDCKib4w3nmlCvLkVXCk3LWUS-fUc14oWE"
$env:EMAIL_USER="hoangthanhsangdp@gmail.com"
$env:EMAIL_PASSWORD="qtxzvfrfjziwwcrc"
```

- Giải thích biến:
  - `SQL_URL`: chuỗi JDBC kết nối SQL Server.
    - `<TEN_SERVER>`: tên máy SQL Server (ví dụ `localhost`, `DESKTOP-ABC123`).
    - `<TEN_INSTANCE>`: tên instance SQL (ví dụ `SQLEXPRESS`).
    - `<TEN_DATABASE>`: tên DB cần dùng (thường là `Fimory`).
  - `SQL_USER`: tài khoản SQL login.
  - `SQL_PASSWORD`: mật khẩu SQL login.
  - `GEMINI_API_KEY`: API key dùng cho chatbot Gemini.
  - `EMAIL_USER`: tài khoản email gửi mail.
  - `EMAIL_PASSWORD`: app password của email.
- Thay placeholder bằng giá trị thật của máy bạn trước khi chạy backend.

## 6.4 Chạy backend

Chạy trong PowerShell tại thư mục gốc `JavaWebFimory`:

```powershell
cmd /c mvnw.cmd spring-boot:run
```

- Backend mặc định chạy cổng `4000` theo `application.yml`.
- Kiểm tra nhanh:
  - `http://localhost:4000/health`
  - `http://localhost:4000/health/db`

## 6.5 Chạy frontend

Chạy trong PowerShell:

```powershell
cd ..\JavaWebFimory\web
npm run dev
```

- Trước lần chạy đầu tiên, cần cài package:
  ```powershell
  npm install
  ```
- Nếu chưa có thư mục `node_modules`, bắt buộc phải chạy `npm install` trước.
- URL dev mặc định của Vite thường là `http://localhost:5173`.
- Frontend dev đã cấu hình proxy `/api` và `/storage` sang backend `http://localhost:4000`.

# 7. Tổng quan API

- Lưu ý chung:
  - API thực tế có prefix `/api` (ví dụ `/api/movies`, `/api/auth/login`).
  - `ApiResponse` được unwrap trước khi trả ra client, nên frontend thường nhận trực tiếp `data`.

- **Health**
  - `GET /health`
  - `GET /health/db`

- **Auth / User**
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `GET /api/auth/role`
  - `GET /api/me`, `POST /api/me`
  - `GET|POST /api/preferences`
  - `GET|POST /api/user/preferences`

- **Movie / Story public**
  - `GET /api/movies`
  - `GET /api/movies/{slug}`
  - `GET /api/movies/{slug}/episodes`
  - `GET /api/stories`
  - `GET /api/stories/{slug}`
  - `GET /api/stories/{seriesId}/chapters`
  - `GET /api/search`

- **Tương tác người dùng**
  - Favorites: `GET|POST|DELETE /api/favorites`
  - History phim: `POST|GET|DELETE /api/history/movie`
  - History truyện: `POST|GET|DELETE /api/history/series`
  - Comment phim/truyện: `GET|POST|PUT|DELETE /api/movies/{id}/comments...`, `/api/stories/{id}/comments...`
  - Rating: `GET|POST /api/movies/{id}/ratings`, `GET|POST /api/stories/{id}/ratings`
  - Notifications: `GET|POST|DELETE /api/notifications`, `POST /api/notifications/read`, `GET /api/notifications/count`

- **Admin**
  - Movies/Stories/Categories CRUD: `/api/admin/...`
  - Users, comments moderation, role-upgrade requests, statistics.
  - Quản lý tập phim/chapter cho nội dung.

- **Upload người dùng**
  - `/api/user/movies`, `/api/user/stories`, `/api/user/stories/{seriesId}/chapters` (multipart).

- **Crawl**
  - `POST /api/crawl/story`
  - `POST /api/crawl/chapter`
  - `POST /api/crawl/cleanup-temp`
  - Một số route crawl phim/admin crawl hiện trả thông báo TODO.

- **Video**
  - `POST /api/videos/upload`
  - `GET /api/videos/{id}`
  - `GET /api/videos/{id}/stream`
  - `GET /api/videos/{id}/variants/{variant}/index.m3u8?token=...`
  - `GET /api/videos/{id}/segments/{variant}/{segmentName}?token=...`

- **Ví dụ request/response**
  - Đăng nhập:
    ```http
    POST /api/auth/login
    Content-Type: application/json

    {
      "email": "user@example.com",
      "password": "123456"
    }
    ```
    ```json
    {
      "id": 1,
      "email": "user@example.com",
      "displayName": "User",
      "role": "Viewer"
    }
    ```
  - Lấy danh sách phim:
    ```http
    GET /api/movies
    ```
    ```json
    [
      {
        "MovieID": 10,
        "Title": "Ten phim",
        "Slug": "ten-phim",
        "PosterURL": "/storage/posters/abc.jpg"
      }
    ]
    ```

# 8. Lỗi thường gặp và cách khắc phục

- **Lỗi database**
  - Sai định dạng `SQL_URL`
    - Nguyên nhân: chuỗi JDBC không đúng cú pháp SQL Server.
    - Dấu hiệu: lỗi kết nối JDBC, timeout hoặc không parse URL.
    - Cách sửa:
      - Dùng đúng mẫu:
        `jdbc:sqlserver://<TEN_SERVER>\<TEN_INSTANCE>;databaseName=<TEN_DATABASE>;encrypt=false;trustServerCertificate=true`
      - Nếu dùng port cố định, có thể chuyển sang:
        `jdbc:sqlserver://<TEN_SERVER>:1433;databaseName=<TEN_DATABASE>;encrypt=false;trustServerCertificate=true`
  - Sai `SQL_USER`/`SQL_PASSWORD`
    - Nguyên nhân: nhập sai tài khoản hoặc SQL login chưa được cấp quyền DB.
    - Dấu hiệu: `Login failed for user ...`.
    - Cách sửa: kiểm tra lại user/pass trong SSMS, cấp quyền vào DB `Fimory`, rồi set lại biến môi trường.
  - SQL Server service chưa chạy
    - Nguyên nhân: dịch vụ `SQL Server (...)` đang stop.
    - Dấu hiệu: connect timeout/refused.
    - Cách sửa: mở `SQL Server Configuration Manager` hoặc `services.msc` và start service SQL Server + SQL Browser (nếu dùng instance).
  - Sai tên server/instance
    - Nguyên nhân: dùng nhầm `<TEN_SERVER>` hoặc `<TEN_INSTANCE>`.
    - Dấu hiệu: không kết nối được dù user/pass đúng.
    - Cách sửa: kiểm tra tên instance trong SSMS (Object Explorer) và cập nhật `SQL_URL`.
  - Database chưa tồn tại
    - Nguyên nhân: chưa chạy file schema nền.
    - Dấu hiệu: `Cannot open database "Fimory" requested by the login`.
    - Cách sửa: tạo DB `Fimory` và chạy file SQL (1) trước.
  - Thiếu bảng bắt buộc
    - Nguyên nhân: script chạy lỗi giữa chừng hoặc chạy nhầm thứ tự.
    - Dấu hiệu: `Invalid object name 'Movies'`, `Invalid column name 'StoryType'`, ...
    - Cách sửa: chạy lại theo thứ tự 1 -> 2 -> 3 -> 4, kiểm tra log lỗi từng file.
  - Chạy file SQL sai thứ tự
    - Nguyên nhân: chạy file mở rộng trước file nền.
    - Dấu hiệu: lỗi object/constraint/index.
    - Cách sửa: reset môi trường test DB, chạy đúng thứ tự như mục 6.2.
  - Lỗi JDBC driver/cấu hình datasource
    - Nguyên nhân: dependency lỗi hoặc app đọc sai biến môi trường.
    - Dấu hiệu: `Failed to configure a DataSource` hoặc class driver không tìm thấy.
    - Cách sửa: dùng `mvnw.cmd` để tải dependency lại; kiểm tra `spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver` và biến `SQL_URL`.
  - Lỗi khi chạy file SQL nền do đoạn placeholder/debug
    - Nguyên nhân: trong file `.txt` có đoạn mẫu chứa `${user.UserID}`, `@UserID` không phải script SQL hoàn chỉnh.
    - Dấu hiệu: `Incorrect syntax near '$'`, `Must declare the scalar variable '@UserID'`.
    - Cách sửa: comment/bỏ qua các đoạn mẫu debug đó rồi chạy lại phần schema hợp lệ; sau đó chạy file (2), (3), (4).

- **Lỗi backend**
  - `mvnw.cmd` not found
    - Nguyên nhân: chạy sai thư mục.
    - Cách sửa: chuyển về thư mục gốc dự án `JavaWebFimory` rồi chạy lại lệnh.
  - Sai phiên bản Java
    - Nguyên nhân: đang dùng Java thấp hơn 21.
    - Dấu hiệu: lỗi build/boot liên quan source/target/release.
    - Cách sửa: cài JDK 21 và kiểm tra `java -version`.
  - Maven tải dependency thất bại
    - Nguyên nhân: mạng, proxy, repo Maven bị chặn.
    - Dấu hiệu: lỗi resolve artifact.
    - Cách sửa: kiểm tra internet/proxy; chạy lại `cmd /c mvnw.cmd -U clean install`.
  - Biến môi trường chưa được nạp
    - Nguyên nhân: set biến ở terminal khác hoặc quên set trước khi chạy.
    - Dấu hiệu: app cố kết nối DB mặc định sai hoặc thiếu Gemini/email config.
    - Cách sửa: set lại biến trong chính cửa sổ PowerShell đang chạy backend.
  - Port đã bị chiếm (`4000`)
    - Nguyên nhân: tiến trình khác dùng cổng 4000.
    - Dấu hiệu: `Port 4000 was already in use`.
    - Cách sửa: dừng tiến trình cũ hoặc đổi `server.port`.
  - Spring Boot fail do lỗi DB
    - Nguyên nhân: datasource không kết nối được.
    - Dấu hiệu: app dừng ngay khi startup, stacktrace liên quan datasource/jdbc.
    - Cách sửa: xử lý các lỗi database ở nhóm trên rồi chạy lại.

- **Lỗi frontend**
  - `npm` is not recognized
    - Nguyên nhân: chưa cài Node.js hoặc PATH chưa đúng.
    - Cách sửa: cài Node.js LTS, mở terminal mới và kiểm tra `node -v`, `npm -v`.
  - Thiếu `node_modules`
    - Nguyên nhân: chưa chạy `npm install`.
    - Dấu hiệu: lỗi không tìm package khi `npm run dev`.
    - Cách sửa: chạy `npm install` trong thư mục `web`.
  - `npm run dev` thất bại
    - Nguyên nhân: lỗi dependency hoặc lockfile.
    - Cách sửa: xóa `node_modules` + cài lại, kiểm tra log lỗi package cụ thể.
  - Frontend không gọi được backend API
    - Nguyên nhân: backend chưa chạy ở `localhost:4000`.
    - Dấu hiệu: UI lên nhưng không có dữ liệu, lỗi network trong DevTools.
    - Cách sửa: chạy backend trước, kiểm tra `/health`; đảm bảo frontend đang dùng proxy `/api`.
  - CORS hoặc sai API base URL
    - Nguyên nhân: chạy frontend ngoài Vite proxy hoặc set `VITE_API_URL` sai.
    - Dấu hiệu: trình duyệt báo CORS, gọi sai host.
    - Cách sửa: trong dev nên để gọi relative `/api`; nếu set `VITE_API_URL`, đảm bảo đúng origin backend và cấu hình CORS tương ứng.
  - Frontend chạy nhưng dữ liệu rỗng do backend/database down
    - Nguyên nhân: backend không truy cập được SQL Server.
    - Dấu hiệu: nhiều API trả lỗi 500 hoặc empty.
    - Cách sửa: kiểm tra lần lượt DB -> backend -> frontend theo chuỗi phụ thuộc.

- **Lỗi kết nối giữa frontend, backend, database**
  - Triệu chứng: frontend mở được nhưng API lỗi, backend log báo SQL lỗi.
  - Quy trình sửa nhanh:
    1. Kiểm tra DB bằng SSMS (đăng nhập được, DB `Fimory` tồn tại, có bảng).
    2. Kiểm tra backend bằng `http://localhost:4000/health` và `/health/db`.
    3. Kiểm tra frontend đang gọi đúng `/api` (tab Network của browser).
    4. Kiểm tra lại biến môi trường `SQL_URL`, `SQL_USER`, `SQL_PASSWORD` trong terminal chạy backend.
