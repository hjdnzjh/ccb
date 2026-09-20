package com.water.ai.meter.portal;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private final PortalService portal;
    @GetMapping public ApiResult<List<Map<String,Object>>> list() {return ApiResult.ok(portal.adminFeedback());}
    @GetMapping("/{id}") public ApiResult<Map<String,Object>> detail(@PathVariable long id) {return ApiResult.ok(portal.adminFeedbackDetail(id));}
    @PostMapping("/{id}/reply") public ApiResult<Boolean> reply(HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,String> body) {
        portal.reply(id,SessionUser.from(req).id(),body.get("reply"),body.get("status"));return ApiResult.ok(true);
    }
}
