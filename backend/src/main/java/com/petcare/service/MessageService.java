package com.petcare.service;

import com.petcare.common.PageResult;
import com.petcare.entity.Message;
import com.petcare.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息通知服务。
 * 订单状态每次变化都往这里塞一条，用户端右上角红点就能实时看到。
 * user_id = 0 是商家后台信箱（催单消息发这里）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    public void send(Long userId, Long orderId, String type, String title, String content) {
        Message message = new Message();
        message.setUserId(userId == null ? Message.ADMIN_USER_ID : userId);
        message.setOrderId(orderId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        messageMapper.insert(message);
        log.debug("消息已发送: userId={}, title={}", userId, title);
    }

    public PageResult<Message> page(Long userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        long total = messageMapper.countByUserId(userId);
        if (total == 0) {
            return PageResult.empty(pageNum, pageSize);
        }
        List<Message> list = messageMapper.selectByUserId(userId, offset, pageSize);
        return PageResult.of(total, pageNum, pageSize, list);
    }

    public long unreadCount(Long userId) {
        return messageMapper.countUnread(userId);
    }

    public void markAllRead(Long userId) {
        messageMapper.markAllRead(userId);
    }
}
