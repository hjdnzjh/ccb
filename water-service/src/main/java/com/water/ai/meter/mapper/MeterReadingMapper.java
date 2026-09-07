package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.entity.MeterReading;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 抄表记录Mapper接口
 */
@Mapper
public interface MeterReadingMapper extends BaseMapper<MeterReading> {

    /**
     * 根据水表ID查询最新抄表记录
     */
    @Select("SELECT * FROM meter_reading WHERE meter_id = #{meterId} AND deleted = 0 " +
            "ORDER BY reading_time DESC LIMIT 1")
    MeterReading selectLatestByMeterId(Long meterId);

    /**
     * 根据水表ID查询历史抄表记录
     */
    @Select("SELECT * FROM meter_reading WHERE meter_id = #{meterId} AND deleted = 0 " +
            "ORDER BY reading_time DESC LIMIT #{limit}")
    List<MeterReading> selectHistoryByMeterId(@Param("meterId") Long meterId, @Param("limit") int limit);

    /**
     * 统计抄表方式分布
     */
    @Select("SELECT reading_type, COUNT(*) as count FROM meter_reading " +
            "WHERE deleted = 0 AND reading_time >= #{startDate} GROUP BY reading_type")
    List<Map<String, Object>> countByReadingType(LocalDateTime startDate);

    /**
     * 统计AI识别成功率
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN confidence >= 0.95 THEN 1 ELSE 0 END) as auto_accept, " +
            "SUM(CASE WHEN confidence >= 0.80 AND confidence < 0.95 THEN 1 ELSE 0 END) as review " +
            "FROM meter_reading WHERE deleted = 0 AND reading_type = 'ai_image' " +
            "AND reading_time >= #{startDate}")
    Map<String, Object> statisticsAiRecognition(LocalDateTime startDate);

    /**
     * 查询待审核记录
     */
    @Select("SELECT * FROM meter_reading WHERE status = 0 AND deleted = 0 ORDER BY reading_time ASC")
    IPage<MeterReading> selectPendingReview(Page<MeterReading> page);

    /**
     * 统计异常抄表
     */
    @Select("SELECT anomaly_type, COUNT(*) as count FROM meter_reading " +
            "WHERE anomaly_flag = 1 AND deleted = 0 AND reading_time >= #{startDate} " +
            "GROUP BY anomaly_type")
    List<Map<String, Object>> countByAnomalyType(LocalDateTime startDate);

    /**
     * 计算平均置信度
     */
    @Select("SELECT AVG(confidence) as avg_confidence FROM meter_reading " +
            "WHERE reading_type = 'ai_image' AND deleted = 0 AND reading_time >= #{startDate}")
    BigDecimal avgConfidence(LocalDateTime startDate);

    /**
     * 按日期统计抄表数量
     */
    @Select("SELECT DATE(reading_time) as date, COUNT(*) as count " +
            "FROM meter_reading WHERE deleted = 0 AND reading_time >= #{startDate} " +
            "GROUP BY DATE(reading_time) ORDER BY date")
    List<Map<String, Object>> countByDate(LocalDateTime startDate);
}