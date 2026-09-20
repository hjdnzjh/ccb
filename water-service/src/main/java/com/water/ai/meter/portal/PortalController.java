package com.water.ai.meter.portal;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.entity.*;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class PortalController {
    private final PortalService portal;
    public record FeedbackBody(Long meterId,String subject,String content) {}
    @GetMapping("/overview") public ApiResult<Map<String,Object>> overview(HttpServletRequest req) {return ApiResult.ok(portal.overview(SessionUser.from(req)));}
    @GetMapping("/bills/{id}") public Bill detail(HttpServletRequest req,@PathVariable long id) {return portal.ownBill(SessionUser.from(req).id(),id);}
    @GetMapping("/bills/{id}/payments") public ApiResult<List<BillPayment>> history(HttpServletRequest req,@PathVariable long id) {return ApiResult.ok(portal.history(SessionUser.from(req).id(),id));}
    @PostMapping("/bills/{id}/pay") public Map<String,Object> pay(HttpServletRequest req,@PathVariable long id,@RequestParam BigDecimal amount,@RequestParam String payMethod,@RequestParam(required=false) String tradeNo) {
        return portal.pay(SessionUser.from(req).id(),id,amount,payMethod,tradeNo);
    }
    @GetMapping("/feedback") public ApiResult<List<Map<String,Object>>> feedback(HttpServletRequest req) { return ApiResult.ok(portal.feedback(SessionUser.from(req).id())); }
    @PostMapping("/feedback") public ApiResult<Map<String,Object>> feedback(HttpServletRequest req,@RequestBody FeedbackBody body) {
        return ApiResult.ok(portal.createFeedback(SessionUser.from(req).id(),body.meterId(),body.subject(),body.content()));
    }
}
