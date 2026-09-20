package com.water.ai.meter.operations;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/anomaly")
public class AnomalyController {
 private final AnomalyOperations service;
 public AnomalyController(AnomalyOperations service){this.service=service;}
 @GetMapping("/list") public ApiResult<Map<String,Object>> list(@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="10")int pageSize,@RequestParam(required=false)String meterNo,@RequestParam(required=false)Integer status,@RequestParam(required=false)String severity){return ApiResult.ok(service.list(pageNum,pageSize,meterNo,status,severity));}
 @GetMapping("/{id}") public ApiResult<Map<String,Object>> detail(@PathVariable long id){return ApiResult.ok(service.detail(id));}
 @PostMapping("/{id}/work-order") public ApiResult<Long> order(@PathVariable long id){return ApiResult.ok(service.workOrder(id));}
 public record Resolution(int status,String note){}
 @PostMapping("/{id}/resolve") public ApiResult<Boolean> resolve(@PathVariable long id,@RequestBody Resolution body,HttpServletRequest request){service.resolve(id,body.status(),body.note(),"用户#"+SessionUser.from(request).id());return ApiResult.ok(true);}
}
