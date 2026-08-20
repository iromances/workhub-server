package cn.aslight.workhub.dao.notification;

import cn.aslight.workhub.model.notification.NotificationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Select("""
            SELECT id,
                   recipient_user_name AS recipientUserName,
                   notification_type AS notificationType,
                   title,
                   content,
                   business_line_code AS businessLineCode,
                   environment_code AS environmentCode,
                   dedupe_key AS dedupeKey,
                   read_flag AS readFlag,
                   read_at AS readAt,
                   created_at AS createdAt
            FROM sys_notification
            WHERE recipient_user_name = #{recipientUserName}
              AND read_flag = 0
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<NotificationEntity> findByRecipient(@Param("recipientUserName") String recipientUserName,
                                             @Param("limit") int limit);

    @Select("""
            SELECT COUNT(1)
            FROM sys_notification
            WHERE recipient_user_name = #{recipientUserName}
              AND read_flag = 0
            """)
    int countUnread(@Param("recipientUserName") String recipientUserName);

    @Insert("""
            INSERT IGNORE INTO sys_notification (
                recipient_user_name,
                notification_type,
                title,
                content,
                business_line_code,
                environment_code,
                dedupe_key,
                read_flag
            ) VALUES (
                #{recipientUserName},
                #{notificationType},
                #{title},
                #{content},
                #{businessLineCode},
                #{environmentCode},
                #{dedupeKey},
                0
            )
            """)
    int insertIgnore(NotificationEntity entity);

    @Update("""
            UPDATE sys_notification
            SET read_flag = 1,
                read_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND recipient_user_name = #{recipientUserName}
            """)
    int markRead(@Param("id") Long id, @Param("recipientUserName") String recipientUserName);

    @Update("""
            UPDATE sys_notification
            SET read_flag = 1,
                read_at = CURRENT_TIMESTAMP
            WHERE recipient_user_name = #{recipientUserName}
              AND read_flag = 0
            """)
    int markAllRead(@Param("recipientUserName") String recipientUserName);
}
