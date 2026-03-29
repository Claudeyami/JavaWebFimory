package com.fimory.api.category;

import com.fimory.api.common.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

@Service
public class CategoryService {

    private final JdbcTemplate jdbcTemplate;

    public CategoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategoryDto> getAll() {
        String nameColumn = resolveCategoryNameColumn();
        String typeColumn = resolveCategoryTypeColumn();
        String sql;
        if (typeColumn != null) {
            sql = "SELECT CategoryID, " + nameColumn + " AS CategoryName, Slug, " + typeColumn + " AS CategoryType FROM Categories ORDER BY CategoryID DESC";
        } else {
            sql = "SELECT CategoryID, " + nameColumn + " AS CategoryName, Slug FROM Categories ORDER BY CategoryID DESC";
        }
        RowMapper<CategoryDto> mapper = (rs, rowNum) -> new CategoryDto(
                rs.getLong("CategoryID"),
                rs.getString("CategoryName"),
                rs.getString("Slug"),
                typeColumn == null ? "Both" : normalizeType(rs.getString("CategoryType"))
        );
        return jdbcTemplate.query(sql, mapper);
    }

    @Transactional
    public CategoryDto create(CategoryUpsertRequest request) {
        String nameColumn = resolveCategoryNameColumn();
        String typeColumn = resolveCategoryTypeColumn();
        String normalizedSlug = ensureUniqueSlug(generateSlug(request.slug(), request.name()), null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql;
        if (typeColumn != null) {
            sql = "INSERT INTO Categories (" + nameColumn + ", Slug, " + typeColumn + ") VALUES (?, ?, ?)";
        } else {
            sql = "INSERT INTO Categories (" + nameColumn + ", Slug) VALUES (?, ?)";
        }

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.name());
            ps.setString(2, normalizedSlug);
            if (typeColumn != null) {
                ps.setString(3, normalizeType(request.type()));
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create category");
        }
        return new CategoryDto(key.longValue(), request.name(), normalizedSlug, normalizeType(request.type()));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryUpsertRequest request) {
        String nameColumn = resolveCategoryNameColumn();
        String typeColumn = resolveCategoryTypeColumn();
        MapRow current = jdbcTemplate.query(
                "SELECT TOP 1 " + nameColumn + " AS CategoryName, Slug FROM Categories WHERE CategoryID = ?",
                rs -> {
                    if (!rs.next()) return null;
                    return new MapRow(rs.getString("CategoryName"), rs.getString("Slug"));
                },
                id
        );
        if (current == null) {
            throw new NotFoundException("Category not found");
        }
        String nextName = request.name();
        String nextSlug = request.slug();
        if (nextSlug == null || nextSlug.isBlank()) {
            nextSlug = current.slug;
        }
        nextSlug = ensureUniqueSlug(generateSlug(nextSlug, nextName), id);

        int updated;
        if (typeColumn != null) {
            String sql = "UPDATE Categories SET " + nameColumn + " = ?, Slug = ?, " + typeColumn + " = ? WHERE CategoryID = ?";
            updated = jdbcTemplate.update(sql, request.name(), nextSlug, normalizeType(request.type()), id);
        } else {
            String sql = "UPDATE Categories SET " + nameColumn + " = ?, Slug = ? WHERE CategoryID = ?";
            updated = jdbcTemplate.update(sql, request.name(), nextSlug, id);
        }
        if (updated == 0) {
            throw new NotFoundException("Category not found");
        }
        return new CategoryDto(id, request.name(), nextSlug, normalizeType(request.type()));
    }

    @Transactional
    public void delete(Long id) {
        int updated = jdbcTemplate.update("DELETE FROM Categories WHERE CategoryID = ?", id);
        if (updated == 0) {
            throw new NotFoundException("Category not found");
        }
    }

    private String resolveCategoryNameColumn() {
        Integer hasCategoryName = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'Categories' AND COLUMN_NAME = 'CategoryName'
                        """,
                Integer.class
        );
        if (hasCategoryName != null && hasCategoryName > 0) {
            return "CategoryName";
        }

        Integer hasName = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'Categories' AND COLUMN_NAME = 'Name'
                        """,
                Integer.class
        );
        if (hasName != null && hasName > 0) {
            return "Name";
        }
        throw new IllegalStateException("Categories table missing both CategoryName and Name columns");
    }

    private String resolveCategoryTypeColumn() {
        Integer hasType = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'Categories' AND COLUMN_NAME IN ('Type', 'CategoryType')
                        """,
                Integer.class
        );
        if (hasType == null || hasType == 0) {
            return null;
        }
        Integer hasCategoryType = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'Categories' AND COLUMN_NAME = 'CategoryType'
                        """,
                Integer.class
        );
        if (hasCategoryType != null && hasCategoryType > 0) {
            return "CategoryType";
        }
        return "Type";
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "Both";
        }
        String t = type.trim().toLowerCase();
        if ("movie".equals(t) || "movies".equals(t)) return "Movie";
        if ("series".equals(t) || "story".equals(t) || "stories".equals(t)) return "Series";
        return "Both";
    }

    private String generateSlug(String incomingSlug, String fallbackName) {
        String source = incomingSlug;
        if (source == null || source.isBlank()) {
            source = fallbackName;
        }
        if (source == null || source.isBlank()) {
            source = "category";
        }
        String base = source.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return base.isBlank() ? "category" : base;
    }

    private String ensureUniqueSlug(String baseSlug, Long excludeCategoryId) {
        String slug = baseSlug;
        int suffix = 1;
        while (isSlugTaken(slug, excludeCategoryId)) {
            slug = baseSlug + "-" + suffix++;
        }
        return slug;
    }

    private boolean isSlugTaken(String slug, Long excludeCategoryId) {
        if (excludeCategoryId == null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM Categories WHERE LOWER(Slug) = LOWER(?)",
                    Integer.class,
                    slug
            );
            return count != null && count > 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM Categories WHERE LOWER(Slug) = LOWER(?) AND CategoryID <> ?",
                Integer.class,
                slug,
                excludeCategoryId
        );
        return count != null && count > 0;
    }

    private record MapRow(String name, String slug) {
    }
}
