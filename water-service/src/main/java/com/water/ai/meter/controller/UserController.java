package com.water.ai.meter.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.common.ApiResult;
import com.water.ai.meter.entity.SysUser;
import com.water.ai.meter.entity.WaterMeter;
import com.water.ai.meter.mapper.SysUserMapper;
import com.water.ai.meter.mapper.WaterMeterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "用户管理", description = "用水用户查询与维护")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper sysUserMapper;
    private final WaterMeterMapper waterMeterMapper;

    @Operation(summary = "用户分页列表")
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String creditLevel,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .like(StringUtils.hasText(phone), SysUser::getPhone, phone)
                .eq(StringUtils.hasText(userType), SysUser::getUserType, userType)
                .eq(StringUtils.hasText(creditLevel), SysUser::getCreditLevel, creditLevel)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getUpdateTime);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        List<SysUser> records = page.getRecords().stream().map(this::maskSecret).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return ApiResult.ok(data);
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public ApiResult<SysUser> detail(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            return ApiResult.fail("用户不存在");
        }
        return ApiResult.ok(maskSecret(user));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public ApiResult<SysUser> create(@RequestBody SysUser body) {
        if (!StringUtils.hasText(body.getUsername())) {
            return ApiResult.fail("用户名不能为空");
        }
        SysUser exists = sysUserMapper.selectByUsername(body.getUsername().trim());
        if (exists != null) {
            return ApiResult.fail("用户名已存在");
        }
        SysUser user = SysUser.builder()
                .username(body.getUsername().trim())
                .password(StringUtils.hasText(body.getPassword()) ? body.getPassword() : "123456")
                .realName(body.getRealName())
                .phone(body.getPhone())
                .email(body.getEmail())
                .userType(StringUtils.hasText(body.getUserType()) ? body.getUserType() : "residential")
                .status(body.getStatus() == null ? 0 : body.getStatus())
                .areaId(body.getAreaId())
                .address(body.getAddress())
                .balance(body.getBalance() == null ? BigDecimal.ZERO : body.getBalance())
                .creditLevel(StringUtils.hasText(body.getCreditLevel()) ? body.getCreditLevel() : "A")
                .remark(body.getRemark())
                .deleted(0)
                .build();
        sysUserMapper.insert(user);
        return ApiResult.ok("创建成功", maskSecret(user));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResult<SysUser> update(@PathVariable Long id, @RequestBody SysUser body) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            return ApiResult.fail("用户不存在");
        }
        if (StringUtils.hasText(body.getRealName())) user.setRealName(body.getRealName());
        if (body.getPhone() != null) user.setPhone(body.getPhone());
        if (body.getEmail() != null) user.setEmail(body.getEmail());
        if (StringUtils.hasText(body.getUserType())) user.setUserType(body.getUserType());
        if (body.getStatus() != null) user.setStatus(body.getStatus());
        if (body.getAreaId() != null) user.setAreaId(body.getAreaId());
        if (body.getAddress() != null) user.setAddress(body.getAddress());
        if (body.getBalance() != null) user.setBalance(body.getBalance());
        if (StringUtils.hasText(body.getCreditLevel())) user.setCreditLevel(body.getCreditLevel());
        if (body.getRemark() != null) user.setRemark(body.getRemark());
        if (StringUtils.hasText(body.getPassword())) user.setPassword(body.getPassword());
        sysUserMapper.updateById(user);
        return ApiResult.ok("更新成功", maskSecret(user));
    }

    @Operation(summary = "用户水表列表")
    @GetMapping("/{id}/meters")
    public ApiResult<List<WaterMeter>> meters(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            return ApiResult.fail("用户不存在");
        }
        List<WaterMeter> meters = waterMeterMapper.selectList(new LambdaQueryWrapper<WaterMeter>()
                .eq(WaterMeter::getUserId, id)
                .eq(WaterMeter::getDeleted, 0)
                .orderByDesc(WaterMeter::getUpdateTime));
        return ApiResult.ok(meters);
    }

    private SysUser maskSecret(SysUser user) {
        if (user == null) return null;
        user.setPassword(null);
        return user;
    }
}
