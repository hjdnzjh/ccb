package com.water.ai.meter.controller;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SessionService sessions;
    @PostMapping("/login") public ApiResult<Map<String,Object>> login(@RequestBody Map<String,String> body) {
        return ApiResult.ok("登录成功",sessions.login(body.get("username"),body.get("password")));
    }
    @GetMapping("/me") public ApiResult<SessionUser> me(HttpServletRequest request) { return ApiResult.ok(SessionUser.from(request)); }
    @PostMapping("/logout") public ApiResult<Boolean> logout(HttpServletRequest request) {
        sessions.logout(AuthorizationFilter.bearer(request));return ApiResult.ok(true);
    }
}
