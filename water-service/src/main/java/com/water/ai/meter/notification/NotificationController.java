package com.water.ai.meter.notification;

import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    public record ReadRequest(List<String> keys,Boolean read) {}
    @GetMapping public ApiResult<NotificationService.Inbox> inbox(HttpServletRequest request){return ApiResult.ok(service.inbox(SessionUser.from(request)));}
    @PostMapping("/read") public ApiResult<Integer> mark(HttpServletRequest request,@RequestBody ReadRequest body){
        if(body.read()==null)throw new IllegalArgumentException("请选择已读或未读状态");
        return ApiResult.ok(service.mark(SessionUser.from(request),body.keys(),body.read()));
    }
}
