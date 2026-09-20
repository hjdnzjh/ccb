package com.water.ai.meter.diagnosis;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequiredArgsConstructor
public class DiagnosisController {
 private final DiagnosisService diagnosis;private final ObservationService observations;private final DiagnosticProbeService probes;private final WorkOrderVerificationService verification;private final Clock clock;
 @GetMapping("/api/v1/diagnosis/policy") public ApiResult<?> policy(){return ApiResult.ok(diagnosis.policy());}
 @PutMapping("/api/v1/diagnosis/policy") public ApiResult<?> save(@RequestBody DiagnosisService.Policy body,HttpServletRequest req){return ApiResult.ok(diagnosis.savePolicy(body,SessionUser.from(req).id()));}
 @GetMapping("/api/v1/diagnosis/cases") public ApiResult<?> cases(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String meterNo,@RequestParam(required=false)String state){return ApiResult.ok(diagnosis.cases(page,size,meterNo,state,null));}
 @GetMapping("/api/v1/diagnosis/cases/{id}") public ApiResult<?> detail(@PathVariable long id){return ApiResult.ok(diagnosis.detail(id,null));}
 @PostMapping("/api/v1/diagnosis/cases/{id}/{action}") public ApiResult<?> action(@PathVariable long id,@PathVariable String action,@RequestBody Map<String,String> body,HttpServletRequest req){return ApiResult.ok(diagnosis.action(id,action,body,SessionUser.from(req)));}
 public record Packet(String packet,String requestKey){}
 @PostMapping("/api/v1/diagnosis/observations") public ApiResult<?> observe(@RequestBody Packet body,HttpServletRequest req){return ApiResult.ok(observations.ingest(body.packet(),body.requestKey(),SessionUser.from(req).id()));}
 @GetMapping("/api/v1/diagnosis/observations") public ApiResult<?> history(@RequestParam long meterId){return ApiResult.ok(observations.history(meterId,LocalDateTime.now(clock)));}
 @PostMapping("/api/v1/diagnosis/process") public ApiResult<?> process(){diagnosis.tick();probes.tick();diagnosis.tick();verification.tick();return ApiResult.ok(Map.of("processed",true));}
 @GetMapping("/api/v1/me/diagnosis/cases") public ApiResult<?> own(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,HttpServletRequest req){return ApiResult.ok(diagnosis.cases(page,size,null,null,SessionUser.from(req).id()));}
 @GetMapping("/api/v1/me/diagnosis/cases/{id}") public ApiResult<?> ownDetail(@PathVariable long id,HttpServletRequest req){return ApiResult.ok(diagnosis.detail(id,SessionUser.from(req).id()));}
}
