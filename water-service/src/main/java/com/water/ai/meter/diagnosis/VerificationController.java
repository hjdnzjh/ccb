package com.water.ai.meter.diagnosis;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/work-order")
public class VerificationController {
 private final WorkOrderVerificationService service;
 @GetMapping("/{id}/verification") public ApiResult<?> list(@PathVariable long id){return ApiResult.ok(service.list(id));}
 @PostMapping("/{id}/verification/review") public ApiResult<?> review(@PathVariable long id,@RequestBody Map<String,String> body,HttpServletRequest req){if(!"followup".equals(body.get("action")))throw new IllegalArgumentException("不支持的复核动作");return ApiResult.ok(Map.of("workOrderId",service.followup(id,body.get("note"),body.get("requestKey"),SessionUser.from(req).id())));}
}
