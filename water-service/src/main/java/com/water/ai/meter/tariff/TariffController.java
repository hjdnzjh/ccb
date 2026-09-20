package com.water.ai.meter.tariff;
import com.water.ai.meter.common.ApiResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tariff")
public class TariffController {
    private final ResidentialTariffService service;
    public TariffController(ResidentialTariffService service){this.service=service;}
    @GetMapping("/profiles") public ApiResult<?> profiles(){return ApiResult.ok(AnnualTariffCalculator.PROFILES);}
    @GetMapping("/meters") public ApiResult<?> meters(@RequestParam(defaultValue="") String search){return ApiResult.ok(service.eligible(search));}
    @GetMapping("/accounts") public ApiResult<?> accounts(){return ApiResult.ok(service.accounts());}
    @GetMapping("/accounts/{id}") public ApiResult<?> detail(@PathVariable long id){return ApiResult.ok(service.detail(id));}
    @PostMapping("/accounts") public ApiResult<?> create(@RequestBody ResidentialTariffService.AccountInput input){return ApiResult.ok(service.create(input));}
}
