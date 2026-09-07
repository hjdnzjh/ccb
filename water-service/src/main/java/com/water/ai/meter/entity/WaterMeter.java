package com.water.ai.meter.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("water_meter")
@Accessors(chain = true)
public class WaterMeter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 水表ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 水表编号
     */
    private String meterNo;

    /**
     * 水表类型: digital-电子式, pointer-指针式, wheel-字轮式
     */
    private String meterType;

    /**
     * 通讯方式: NB-IoT, LoRa, 4G, wired-有线
     */
    private String commType;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 区域ID
     */
    private Long areaId;

    /**
     * 安装地址
     */
    private String installAddress;

    /**
     * 安装日期
     */
    private LocalDateTime installDate;

    /**
     * 口径
     */
    private String caliber;

    /**
     * 生产厂家
     */
    private String manufacturer;

    /**
     * 表具状态: 0-正常, 1-故障, 2-停用, 3-更换
     */
    private Integer status;

    /**
     * 当前读数
     */
    private BigDecimal currentReading;

    /**
     * 上次读数
     */
    private BigDecimal lastReading;

    /**
     * 上次抄表时间
     */
    private LocalDateTime lastReadingTime;

    /**
     * 设备IMEI
     */
    private String deviceImei;

    /**
     * SIM卡号
     */
    private String simCard;

    /**
     * 信号强度
     */
    private Integer signalStrength;

    /**
     * 电池电量
     */
    private Integer batteryLevel;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 备注
     */
    private String remark;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;
}