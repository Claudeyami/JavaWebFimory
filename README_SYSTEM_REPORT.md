# Fimory Java Backend - System Report

Tai lieu nay tong hop tong the he thong backend de ban dung khi bao cao/do an.

## 1) Tong quan he thong

Backend duoc xay dung theo huong REST API cho nen tang xem phim/truyen, ho tro:

- Quan ly nguoi dung, xac thuc, phan quyen
- Quan ly phim/truyen/the loai/chuong/tap
- Tuong tac nguoi dung: binh luan, danh gia, yeu thich, lich su
- Thong bao, diem kinh nghiem, role upgrade request
- Khoi admin: duyet noi dung, thong ke, quan ly user/comment
- Video module moi: upload video, transcode HLS, stream bao ve token

## 2) Cong nghe su dung

- Java 21 (tuong thich yeu cau Java 17+)
- Spring Boot 3.3.5
- Spring Web (REST API)
- Spring Data JPA (ORM)
- Spring Security (bao mat API)
- SQL Server JDBC driver
- FFmpeg (video transcoding + watermark + HLS)
- Maven (build, dependency management)

## 3) Kien truc tong the

He thong theo mo hinh layer:

1. `Controller`:
- Nhan request HTTP, map endpoint, tra response.

2. `Service`:
- Chua business logic, validate nghiep vu, xu ly use-case.

3. `Repository`:
- Truy van DB thong qua Spring Data JPA hoac JDBC.

4. `Domain/Entity`:
- Mapping bang CSDL thanh doi tuong Java.

5. `Security + Config + Common`:
- Xac thuc/pham vi truy cap, cau hinh he thong, xu ly loi dung chung.

## 4) Cau truc thu muc chinh

Duong dan goc: `src/main/java/com/fimory/api`

- `admin`: logic cho admin dashboard, duyet noi dung, thong ke.
- `auth`: dang ky/dang nhap/otp/quen mat khau.
- `category`: API va logic the loai.
- `comment`: binh luan phim/truyen + phan hoi.
- `common`: `ApiResponse`, `ApiError`, `GlobalExceptionHandler`.
- `config`: config app, health check, storage/video async config.
- `crawl`: endpoint crawl du lieu external ve phim/truyen/tap.
- `domain`: entity map CSDL.
- `experience`: diem kinh nghiem nguoi dung.
- `favorite`: yeu thich phim/truyen.
- `history`: lich su xem/doc.
- `movie`: nghiep vu phim.
- `notification`: thong bao.
- `rating`: danh gia phim/truyen.
- `repository`: JPA repository.
- `security`: bo loc auth header, security chain.
- `series`: nghiep vu truyen/series.
- `user`: thong tin user, preference, upload cua user.
- `video`: module video streaming moi (upload/transcode/stream token).

## 5) Cac endpoint nghiep vu chinh (nhom theo module)

### 5.1 Auth
- `/auth/register`, `/auth/login`, `/auth/role`
- `/auth/forgot-password`, `/auth/reset-password`
- `/send-otp`, `/verify-otp`

### 5.2 Noi dung phim/truyen
- Phim: `/movies`, `/movies/{slug}`, `/movies/{slug}/episodes`
- Truyen: `/stories`, `/stories/{slug}`, `/stories/{seriesId}/chapters`
- The loai: `/categories`, `/categories/{id}/movies`, `/categories/{id}/series`

### 5.3 Tuong tac nguoi dung
- Comment: `/movies/{id}/comments`, `/stories/{id}/comments`
- Rating: `/movies/{id}/ratings`, `/stories/{id}/ratings`
- Favorite: `/favorites`, `/movies/{id}/favorite`, `/stories/{id}/favorite`
- History: `/history/movie`, `/history/series`
- Notification: `/notifications`, `/notifications/count`, `/notifications/read`

### 5.4 User profile + user upload
- `/me`, `/preferences`, `/user/preferences`
- `/user/role-upgrade-request`, `/user/role-upgrade-requests`
- Upload user content: `/user/movies`, `/user/stories`, `/user/movies/{id}/episodes`

### 5.5 Admin
- `/admin/movies`, `/admin/stories`, `/admin/categories`, `/admin/users`
- `/admin/comments`
- `/admin/role-upgrade-requests`
- `/admin/stats/movies/views`, `/admin/stats/movies/engagement`, `/admin/statistics`

### 5.6 Video module moi (HLS)
- `POST /api/videos/upload`
- `GET /api/videos/{id}`
- `GET /api/videos/{id}/stream` (can auth)
- `GET /api/videos/{id}/variants/{variant}/index.m3u8?token=...`
- `GET /api/videos/{id}/segments/{variant}/{segmentName}?token=...`
- `GET /api/videos/{id}/watermark?token=...`

## 6) Bao mat va xac thuc

File chinh: `security/SecurityConfig.java`

- Kieu session: stateless
- Disable CSRF cho API
- Auth boi custom header filter (`HeaderAuthFilter`)
- Nhom endpoint public duoc `permitAll` (login, movie list, crawling endpoint can thiet, segment token endpoint)
- Cac endpoint con lai yeu cau authenticated
- Tra ve JSON ro rang cho 401/403

## 7) Xu ly loi he thong

File chinh: `common/GlobalExceptionHandler.java`

- Loi nghiep vu:
  - `IllegalArgumentException` -> 400
  - `IllegalStateException` -> 409
- Validation error -> 400 + chi tiet field
- Loi khong duoc du doan -> 500

Muc tieu: frontend nhan duoc ma loi dung de xu ly UI, khong bi "tat ca deu 500".

## 8) Module Video (chi tiet van hanh)

### 8.1 Entity va DB

`domain/VideoEntity.java` map bang `Videos`:

- `VideoID`
- `OriginalPath`
- `HlsPath`
- `Status` (`PROCESSING`, `READY`, `FAILED`)
- `CreatedAt`
- `ErrorMessage`

`video/VideoSchemaInitializer.java` tu tao bang/index neu chua ton tai.

### 8.2 Upload flow

1. User goi `POST /api/videos/upload` voi `multipart file`.
2. `VideoService` validate:
- duoi file (`.mp4`, `.mov`)
- kich thuoc (toi da 5GB theo code module video)
3. File goc duoc luu vao storage local.
4. Tao row `Videos` voi status `PROCESSING`.
5. Day xu ly async qua `VideoProcessingService`.

### 8.3 Transcode async bang FFmpeg

`video/FfmpegService.java`:

- Tao 2 profile: `720p`, `480p`
- Overlay logo static o goc phai duoi
- Xuat HLS VOD:
  - `index.m3u8`
  - `segment_XXX.ts`
- Sinh `master.m3u8` cho ABR.

### 8.4 Streaming flow

1. Client goi `/api/videos/{id}/stream` (can auth).
2. Server phat token stream ngan han (HMAC SHA256).
3. Server rewrite master playlist thanh URL co token.
4. Client tai variant playlist + segment qua endpoint tokenized.
5. Segment support HTTP Range + cache header.

### 8.5 Dynamic watermark

`/api/videos/{id}/watermark` tra ve watermark hint:

- `userId`
- timestamp
- recommendation cho overlay tren player.

Hai huong:
- Frontend overlay: re, de scale, chong quay man hinh o muc co ban.
- Server-side personalized stream: an toan hon, nhung ton CPU/GPU.

## 9) Storage va config runtime

`application.yml`:

- `server.port = 4000`
- Multipart size global: `1000MB`
- `app.video.*`:
  - `storage-root`
  - `original-dir`
  - `hls-dir`
  - `ffmpeg-path`, `ffprobe-path`
  - `watermark-logo-path`
  - `hls-segment-duration-sec`
  - `stream-token-ttl-sec`
  - `stream-token-secret`

Thu muc storage video:

```
video-storage/
  original/
    <uuid>.mp4
  hls/
    <videoId>/
      master.m3u8
      720p/index.m3u8 + .ts
      480p/index.m3u8 + .ts
```

## 10) Cac file quan trong va y nghia

### 10.1 Core app
- `FimoryApiApplication.java`: diem vao Spring Boot.

### 10.2 Security
- `security/SecurityConfig.java`: policy truy cap endpoint.
- `security/HeaderAuthFilter.java`: doc auth tu header.
- `security/CurrentUserProvider.java`: lay user hien tai cho business.

### 10.3 Common
- `common/ApiResponse.java`: response success format.
- `common/ApiError.java`: response error format.
- `common/GlobalExceptionHandler.java`: map exception -> HTTP code.

### 10.4 Movie/Series/User modules
- `movie/MovieController.java`, `movie/MovieService.java`
- `series/SeriesController.java`, `series/SeriesService.java`
- `user/UserController.java`, `user/UserUploadController.java`, `user/UserService.java`

### 10.5 Admin
- `admin/AdminController.java`: quan ly tong hop + thong ke.

### 10.6 Video module
- `video/VideoController.java`: API upload + stream.
- `video/VideoService.java`: orchestration upload/token/playlist rewrite.
- `video/VideoProcessingService.java`: async transcode.
- `video/FfmpegService.java`: command ffmpeg.
- `video/VideoStorageService.java`: path/luu file.
- `video/VideoStreamTokenService.java`: ky/verify stream token.
- `video/VideoSchemaInitializer.java`: tao bang `Videos`.
- `config/VideoProperties.java`, `config/VideoModuleConfig.java`: config + thread pool.

## 11) Cach he thong van hanh dau-cuoi

1. Frontend goi API.
2. Security filter xac dinh quyen.
3. Controller tiep nhan request va parse input.
4. Service xu ly business logic, goi repository/JDBC.
5. Neu upload video:
- save file -> DB `PROCESSING` -> async ffmpeg -> cap nhat `READY/FAILED`.
6. GlobalExceptionHandler dam bao loi tra ra format thong nhat.

## 12) Performance va scale (de tra loi hoi dong)

### Hien tai
- Local disk storage.
- Async transcode bang thread pool app.
- HLS segment co cache header.

### Huong production

1. Chuyen storage sang S3-compatible.
2. Dat CDN truoc segment HLS.
3. Tach transcode thanh worker service + queue (RabbitMQ/Kafka/SQS).
4. Them chunk upload (TUS hoac multipart chunk API).
5. Tang profile ABR (360/720/1080) theo thiet bi.
6. Theo doi metrics: transcode time, segment throughput, error rate.

## 13) Lenh build/chay co ban

Build:

```powershell
cmd /c mvnw.cmd -DskipTests compile
```

Run:

```powershell
cmd /c mvnw.cmd spring-boot:run
```

## 14) Cau hoi hay bi hoi va cach tra loi nhanh

1. Tai sao dung HLS ma khong mp4 truc tiep?
- HLS ho tro adaptive bitrate, streaming on-demand tot hon, de cache qua CDN.

2. Tai sao can token cho segment?
- Tranh link segment bi share truc tiep, gioi han thoi gian truy cap.

3. Watermark static va dynamic khac nhau gi?
- Static: dong dau trong video, kho xoa.
- Dynamic: hien user/time khi xem, truy vet leak tot hon.

4. Vi sao xu ly video async?
- Upload khong bi block, UX tot hon, app khong bi nghen request thread.

5. Neu user upload file rat lon?
- Dung chunk upload + resume, luu tam chunk, merge roi moi transcode.

## 15) Ghi chu quan trong

- Khong expose truc tiep thu muc `video-storage` qua static web.
- Secret token phai set bang env o production.
- Nen bo mat khau DB khoi `application.yml` va dung secret manager.

