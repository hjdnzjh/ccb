package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.entity.WaterMeter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 水表Mapper接口
 */
@Mapper
public interface WaterMeterMapper extends BaseMapper<WaterMeter> {

    /**
     * 根据水表编号查询
     */
    @Select("SELECT * FROM water_meter WHERE meter_no = #{meterNo} AND deleted = 0")
    WaterMeter selectByMeterNo(String meterNo);

    /**
     * 根据用户ID查询水表列表
     */
    @Select("SELECT * FROM water_meter WHERE user_id = #{userId} AND deleted = 0")
    List<WaterMeter> selectByUserId(Long userId);

    /**
     * 根据区域ID查询水表列表
     */
    @Select("SELECT * FROM water_meter WHERE area_id = #{areaId} AND deleted = 0")
    List<WaterMeter> selectByAreaId(Long areaId);

    /**
     * 统计各类型水表数量
     */
    @Select("SELECT meter_type, COUNT(*) as count FROM water_meter WHERE deleted = 0 GROUP BY meter_type")
    List<Map<String, Object>> countByMeterType();

    /**
     * 统计各通讯方式水表数量
     */
    @Select("SELECT comm_type, COUNT(*) as count FROM water_meter WHERE deleted = 0 GROUP BY comm_type")
    List<Map<String, Object>> countByCommType();

    /**
     * 统计各状态水表数量
     */
    @Select("SELECT status, COUNT(*) as count FROM water_meter WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /**
     * 更新水表读数
     */
    @Update("UPDATE water_meter SET current_reading = #{reading}, last_reading = current_reading, " +
            "last_reading_time = #{readingTime}, update_time = NOW() WHERE id = #{id}")
    int updateReading(@Param("id") Long id, @Param("reading") BigDecimal reading, 
                      @Param("readingTime") LocalDateTime readingTime);

    /**
     * 更新水表状态
     */
    @Update("UPDATE water_meter SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 查询待抄表水表(超过30天未抄表)
     */
    @Select("SELECT * FROM water_meter WHERE deleted = 0 AND status = 0 " +
            "AND (last_reading_time IS NULL OR last_reading_time < #{deadline})")
    List<WaterMeter> selectPendingReading(LocalDateTime deadline);

    /**
     * 查询故障水表
     */
    @Select("SELECT * FROM water_meter WHERE status = 1 AND deleted = 0")
    List<WaterMeter> selectFaultMeters();

    /**
     * 统计区域用水量
     */
    @Select("SELECT area_id, SUM(current_reading - last_reading) as total_usage " +
            "FROM water_meter WHERE deleted = 0 AND last_reading_time >= #{startDate} " +
            "GROUP BY area_id")
    List<Map<String, Object>> sumUsageByArea(LocalDateTime startDate);
}