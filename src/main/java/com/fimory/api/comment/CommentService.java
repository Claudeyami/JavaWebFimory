package com.fimory.api.comment;

import com.fimory.api.common.NotFoundException;
import com.fimory.api.common.UnauthorizedException;
import com.fimory.api.domain.MovieCommentEntity;
import com.fimory.api.domain.SeriesCommentEntity;
import com.fimory.api.repository.MovieCommentRepository;
import com.fimory.api.repository.SeriesCommentRepository;
import com.fimory.api.repository.UserRepository;
import com.fimory.api.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentService {

    private final MovieCommentRepository movieCommentRepository;
    private final SeriesCommentRepository seriesCommentRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public CommentService(MovieCommentRepository movieCommentRepository,
                          SeriesCommentRepository seriesCommentRepository,
                          UserRepository userRepository,
                          JdbcTemplate jdbcTemplate) {
        this.movieCommentRepository = movieCommentRepository;
        this.seriesCommentRepository = seriesCommentRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CommentDto> getMovieComments(Long movieId) {
        List<MovieCommentEntity> entities = movieCommentRepository.findByMovieIdOrderByCreatedAtDesc(movieId);
        java.util.Set<Long> ids = entities.stream().map(MovieCommentEntity::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Long> parentById = new java.util.HashMap<>();
        for (MovieCommentEntity entity : entities) {
            parentById.put(entity.getId(), entity.getParentCommentId());
        }
        return entities.stream()
                .map(entity -> {
                    if (entity.getParentCommentId() != null) {
                        if (entity.getParentCommentId().equals(entity.getId())
                                || !ids.contains(entity.getParentCommentId())
                                || hasCycle(entity.getId(), parentById)) {
                            entity.setParentCommentId(null);
                        }
                    }
                    return toMovieDto(entity);
                })
                .toList();
    }

    @Transactional
    public CommentDto createMovieComment(Long movieId, AuthenticatedUser user, CommentRequest request) {
        MovieCommentEntity entity = new MovieCommentEntity();
        entity.setMovieId(movieId);
        entity.setUserId(user.userId());
        entity.setParentCommentId(resolveValidMovieParentId(movieId, request.parentCommentId()));
        entity.setContent(request.content());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        MovieCommentEntity saved = movieCommentRepository.save(entity);
        notifyMovieReply(saved, user);
        return toMovieDto(saved);
    }

    @Transactional
    public CommentDto updateMovieComment(Long movieId, Long commentId, AuthenticatedUser user, CommentRequest request) {
        MovieCommentEntity entity = movieCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!entity.getMovieId().equals(movieId)) {
            throw new NotFoundException("Comment not found in movie");
        }

        if (!entity.getUserId().equals(user.userId()) && !"ADMIN".equalsIgnoreCase(user.role())) {
            throw new UnauthorizedException("You do not own this comment");
        }

        entity.setParentCommentId(resolveValidMovieParentId(movieId, request.parentCommentId(), entity.getId()));
        entity.setContent(request.content());
        entity.setUpdatedAt(LocalDateTime.now());
        return toMovieDto(movieCommentRepository.save(entity));
    }

    @Transactional
    public void deleteMovieComment(Long movieId, Long commentId, AuthenticatedUser user) {
        MovieCommentEntity entity = movieCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!entity.getMovieId().equals(movieId)) {
            throw new NotFoundException("Comment not found in movie");
        }

        if (!entity.getUserId().equals(user.userId()) && !"ADMIN".equalsIgnoreCase(user.role())) {
            throw new UnauthorizedException("You do not own this comment");
        }

        movieCommentRepository.delete(entity);
    }

    public List<CommentDto> getSeriesComments(Long seriesId) {
        List<SeriesCommentEntity> entities = seriesCommentRepository.findBySeriesIdOrderByCreatedAtDesc(seriesId);
        java.util.Set<Long> ids = entities.stream().map(SeriesCommentEntity::getId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Long> parentById = new java.util.HashMap<>();
        for (SeriesCommentEntity entity : entities) {
            parentById.put(entity.getId(), entity.getParentCommentId());
        }
        return entities.stream()
                .map(entity -> {
                    if (entity.getParentCommentId() != null) {
                        if (entity.getParentCommentId().equals(entity.getId())
                                || !ids.contains(entity.getParentCommentId())
                                || hasCycle(entity.getId(), parentById)) {
                            entity.setParentCommentId(null);
                        }
                    }
                    return toSeriesDto(entity);
                })
                .toList();
    }

    @Transactional
    public CommentDto createSeriesComment(Long seriesId, AuthenticatedUser user, CommentRequest request) {
        SeriesCommentEntity entity = new SeriesCommentEntity();
        entity.setSeriesId(seriesId);
        entity.setUserId(user.userId());
        entity.setParentCommentId(resolveValidSeriesParentId(seriesId, request.parentCommentId()));
        entity.setContent(request.content());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        SeriesCommentEntity saved = seriesCommentRepository.save(entity);
        notifySeriesReply(saved, user);
        return toSeriesDto(saved);
    }

    @Transactional
    public CommentDto updateSeriesComment(Long seriesId, Long commentId, AuthenticatedUser user, CommentRequest request) {
        SeriesCommentEntity entity = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!entity.getSeriesId().equals(seriesId)) {
            throw new NotFoundException("Comment not found in story");
        }

        if (!entity.getUserId().equals(user.userId()) && !"ADMIN".equalsIgnoreCase(user.role())) {
            throw new UnauthorizedException("You do not own this comment");
        }

        entity.setParentCommentId(resolveValidSeriesParentId(seriesId, request.parentCommentId(), entity.getId()));
        entity.setContent(request.content());
        entity.setUpdatedAt(LocalDateTime.now());
        return toSeriesDto(seriesCommentRepository.save(entity));
    }

    @Transactional
    public void deleteSeriesComment(Long seriesId, Long commentId, AuthenticatedUser user) {
        SeriesCommentEntity entity = seriesCommentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));

        if (!entity.getSeriesId().equals(seriesId)) {
            throw new NotFoundException("Comment not found in story");
        }

        if (!entity.getUserId().equals(user.userId()) && !"ADMIN".equalsIgnoreCase(user.role())) {
            throw new UnauthorizedException("You do not own this comment");
        }

        seriesCommentRepository.delete(entity);
    }

    private CommentDto toMovieDto(MovieCommentEntity entity) {
        var userOpt = userRepository.findById(entity.getUserId());
        String email = userOpt.map(user -> user.getEmail()).orElse("unknown@fimory.local");
        String username = userOpt.map(user -> user.getDisplayName()).orElse("unknown");
        String role = userOpt.map(user -> user.getRole() != null ? user.getRole().getName() : "Viewer").orElse("Viewer");
        return new CommentDto(
                entity.getId(),
                entity.getUserId(),
                email,
                entity.getParentCommentId(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                false,
                username,
                null,
                normalizeRole(role),
                1,
                0,
                0,
                null
        );
    }

    private CommentDto toSeriesDto(SeriesCommentEntity entity) {
        var userOpt = userRepository.findById(entity.getUserId());
        String email = userOpt.map(user -> user.getEmail()).orElse("unknown@fimory.local");
        String username = userOpt.map(user -> user.getDisplayName()).orElse("unknown");
        String role = userOpt.map(user -> user.getRole() != null ? user.getRole().getName() : "Viewer").orElse("Viewer");
        return new CommentDto(
                entity.getId(),
                entity.getUserId(),
                email,
                entity.getParentCommentId(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                false,
                username,
                null,
                normalizeRole(role),
                1,
                0,
                0,
                null
        );
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return "Viewer";
        }
        String normalized = rawRole.trim().toUpperCase();
        return switch (normalized) {
            case "ADMIN" -> "Admin";
            case "UPLOADER" -> "Uploader";
            case "AUTHOR" -> "Author";
            case "TRANSLATOR" -> "Translator";
            case "REUP" -> "Reup";
            case "USER", "VIEWER" -> "Viewer";
            default -> normalized.substring(0, 1) + normalized.substring(1).toLowerCase();
        };
    }

    private Long resolveValidMovieParentId(Long movieId, Long parentCommentId) {
        return resolveValidMovieParentId(movieId, parentCommentId, null);
    }

    private Long resolveValidMovieParentId(Long movieId, Long parentCommentId, Long selfId) {
        if (parentCommentId == null) {
            return null;
        }
        if (selfId != null && parentCommentId.equals(selfId)) {
            return null;
        }
        MovieCommentEntity parent = movieCommentRepository.findById(parentCommentId).orElse(null);
        if (parent == null || !movieId.equals(parent.getMovieId())) {
            return null;
        }
        if (selfId != null && parent.getParentCommentId() != null && parent.getParentCommentId().equals(selfId)) {
            return null;
        }
        return parentCommentId;
    }

    private Long resolveValidSeriesParentId(Long seriesId, Long parentCommentId) {
        return resolveValidSeriesParentId(seriesId, parentCommentId, null);
    }

    private Long resolveValidSeriesParentId(Long seriesId, Long parentCommentId, Long selfId) {
        if (parentCommentId == null) {
            return null;
        }
        if (selfId != null && parentCommentId.equals(selfId)) {
            return null;
        }
        SeriesCommentEntity parent = seriesCommentRepository.findById(parentCommentId).orElse(null);
        if (parent == null || !seriesId.equals(parent.getSeriesId())) {
            return null;
        }
        if (selfId != null && parent.getParentCommentId() != null && parent.getParentCommentId().equals(selfId)) {
            return null;
        }
        return parentCommentId;
    }

    private boolean hasCycle(Long startId, java.util.Map<Long, Long> parentById) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Long current = startId;
        while (current != null) {
            if (!visited.add(current)) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private void notifyMovieReply(MovieCommentEntity comment, AuthenticatedUser actor) {
        Long parentId = comment.getParentCommentId();
        if (parentId == null) {
            return;
        }
        MovieCommentEntity parent = movieCommentRepository.findById(parentId).orElse(null);
        if (parent == null || parent.getUserId() == null || parent.getUserId().equals(comment.getUserId())) {
            return;
        }
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                comment.getMovieId()
        );
        String movieTitle = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : "phim",
                comment.getMovieId()
        );
        String author = actor.email() == null ? "Người dùng" : actor.email();
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                VALUES (?, ?, ?, ?, ?, 0, GETDATE())
                """,
                parent.getUserId(),
                "CommentReplied",
                "Có phản hồi bình luận",
                author + " đã trả lời bình luận của bạn ở phim \"" + movieTitle + "\"",
                slug == null || slug.isBlank() ? "/movies" : "/watch/" + slug
        );
    }

    private void notifySeriesReply(SeriesCommentEntity comment, AuthenticatedUser actor) {
        Long parentId = comment.getParentCommentId();
        if (parentId == null) {
            return;
        }
        SeriesCommentEntity parent = seriesCommentRepository.findById(parentId).orElse(null);
        if (parent == null || parent.getUserId() == null || parent.getUserId().equals(comment.getUserId())) {
            return;
        }
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                comment.getSeriesId()
        );
        String storyTitle = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : "truyện",
                comment.getSeriesId()
        );
        String author = actor.email() == null ? "Người dùng" : actor.email();
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                VALUES (?, ?, ?, ?, ?, 0, GETDATE())
                """,
                parent.getUserId(),
                "CommentReplied",
                "Có phản hồi bình luận",
                author + " đã trả lời bình luận của bạn ở truyện \"" + storyTitle + "\"",
                slug == null || slug.isBlank() ? "/stories" : "/stories/" + slug
        );
    }
}
