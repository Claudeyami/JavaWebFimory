# Implemented Endpoints (MVP)

## Health
- `GET /health`

## Auth + User
- `POST /auth/register`
- `POST /auth/login`
- `GET /auth/role`
- `POST /auth/forgot-password` (TODO body)
- `POST /auth/reset-password` (TODO body)
- `POST /send-otp` (TODO body)
- `POST /verify-otp` (TODO body)
- `GET /me`
- `POST /me`
- `POST /me/avatar` (TODO body)
- `GET /preferences`
- `POST /preferences`
- `GET /user/preferences`
- `POST /user/preferences`

## Movies / Stories Public
- `GET /movies`
- `GET /movies/{slug}`
- `GET /movies/{slug}/episodes`
- `GET /stories`
- `GET /stories/{slug}`
- `GET /stories/{seriesId}/chapters`
- `GET /search`
- `GET /movies/search`

## Interaction
- `GET/POST/PUT/DELETE /movies/{id}/comments...` (TODO body)
- `GET/POST /movies/{id}/ratings` (TODO body)
- `GET/POST/PUT/DELETE /stories/{id}/comments...` (TODO body)
- `GET/POST /stories/{id}/ratings`
- `POST /stories/{id}/rating`
- `GET/POST/DELETE /favorites` (TODO body)
- `POST/GET/DELETE /history/movie` (TODO body)
- `POST/GET/DELETE /history/series` (TODO body)
- `GET/POST/DELETE /notifications` (TODO body)
- `POST /notifications/read` (TODO body)
- `GET /notifications/count` (TODO body)

## Admin
- `GET/POST/PUT/DELETE /admin/movies`
- `GET/POST/PUT/DELETE /admin/stories`
- `GET/POST/PUT/DELETE /admin/categories`
- `GET /admin/users`
- `GET /admin/comments` + `POST /admin/comments/{commentId}/approve` + `DELETE /admin/comments/{commentId}` (TODO body)
- `GET /admin/role-upgrade-requests` + `POST /admin/role-upgrade-requests/{requestId}/approve|reject` (TODO body)
- `GET /admin/statistics` (TODO body)
- `GET /admin/stats/movies/views` (TODO body)
- `POST /admin/movies/{movieId}/episodes` (TODO body)
- `POST /admin/stories/{seriesId}/chapters` (TODO body)

## Crawl
- `GET /crawl/movie/{tmdbId}` (TODO body)
- `POST /crawl/movie` (TODO body)
- `POST /crawl/story` (TODO body)
- `POST /crawl/chapter` (TODO body)
- `POST /admin/stories/{seriesId}/chapters/crawl` (TODO body)
- `POST /admin/movies/{movieId}/episodes/crawl` (TODO body)
