package com.petcare.mapper;

import com.petcare.entity.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息 Mapper。user_id = 0 代表商家后台信箱。
 */
public interface MessageMapper {

    int insert(Message message);

    List<Message> selectByUserId(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countByUserId(@Param("userId") Long userId);

    long countUnread(@Param("userId") Long userId);

    int markAllRead(@Param("userId") Long userId);
}
