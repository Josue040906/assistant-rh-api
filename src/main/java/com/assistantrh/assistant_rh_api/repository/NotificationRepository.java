        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Récupère toutes les notifications d'un utilisateur.
     */
    public List<Map<String, Object>> findByUserId(Integer userId) {

        String sql = """
            SELECT
                id,
                user_id,
                titre,
                message,
                lu,
                date_creation
            FROM notification
            WHERE user_id = ?
            ORDER BY date_creation DESC, id DESC
            """;

        return jdbcTemplate.queryForList(sql, userId);
    }

    /**
     * Compte les notifications non lues d'un utilisateur.
     */
    public long countUnreadByUserId(Integer userId) {

        String sql = """
            SELECT COUNT(*)
            FROM notification
            WHERE user_id = ?
              AND lu = false
            """;

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                userId
        );

        return count != null ? count : 0L;
    }

    /**
     * Marque une notification comme lue.
     * La notification doit appartenir à l'utilisateur concerné.
     */
    public int markAsRead(
            Integer notificationId,
            Integer userId
    ) {

        String sql = """
            UPDATE notification
            SET lu = true
            WHERE id = ?
              AND user_id = ?
            """;

        return jdbcTemplate.update(
                sql,
                notificationId,
                userId
        );
    }

    /**
     * Marque toutes les notifications d'un utilisateur comme lues.
     */
    public int markAllAsRead(Integer userId) {

        String sql = """
            UPDATE notification
            SET lu = true
            WHERE user_id = ?
              AND lu = false
            """;

        return jdbcTemplate.update(sql, userId);
    }

    /**
     * Crée une notification pour un utilisateur.
     */
    public int create(
            Integer userId,
            String titre,
            String message
    ) {

        String sql = """
            INSERT INTO notification (
                user_id,
                titre,
                message
            )
            VALUES (?, ?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                userId,
                titre,
                message
        );
    }

    /**
     * Récupère tous les utilisateurs auxquels
     * une notification doit être envoyée.
     */
    public List<Integer> findAllUserIds() {

        String sql = """
            SELECT id
            FROM utilisateur
            ORDER BY id
            """;

        return jdbcTemplate.queryForList(
                sql,
                Integer.class
        );
    }
}
