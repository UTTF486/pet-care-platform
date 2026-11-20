package com.petcare.controller;

import com.petcare.common.PageResult;
import com.petcare.common.Result;
import com.petcare.common.UserContext;
import com.petcare.entity.Message;
import com.petcare.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 消息中心接口。
 * 管理员（商家）看的是 user_id = 0 的信箱，普通用户看自己的。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 当前用户的消息列表 */
    @GetMapping
    public Result<PageResult<Message>> list(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.isAdmin() ? Message.ADMIN_USER_ID : UserContext.getUserId();
        return Result.success(messageService.page(userId, pageNum, pageSize));
    }

    /** 未读数，用于导航栏红点 */
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        Long userId = UserContext.isAdmin() ? Message.ADMIN_USER_ID : UserContext.getUserId();
        return Result.success(Collections.singletonMap("count", messageService.unreadCount(userId)));
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        Long userId = UserContext.isAdmin() ? Message.ADMIN_USER_ID : UserContext.getUserId();
        messageService.markAllRead(userId);
        return Result.success();
    }
}
