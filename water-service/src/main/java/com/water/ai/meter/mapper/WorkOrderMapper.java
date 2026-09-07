package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.water.ai.meter.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    @Select("SELECT status, COUNT(*) AS cnt FROM work_order WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("<script>" +
            "SELECT wo.id, wo.order_no AS orderNo, wo.order_type AS orderType, " +
            "wo.anomaly_id AS anomalyId, wo.meter_id AS meterId, wo.user_id AS userId, " +
            "wo.area_id AS areaId, wo.title, wo.description, " +
            "wo.handler_id AS handlerId, wo.handler_name AS handlerName, " +
            "wo.priority, wo.status, wo.result, wo.rating, wo.feedback, wo.remark, " +
            "wo.create_time AS createTime, wo.dispatch_time AS dispatchTime, " +
            "wo.accept_time AS acceptTime, wo.complete_time AS completeTime, " +
            "m.meter_no AS meterNo, m.install_address AS installAddress, " +
            "a.area_name AS areaName, a.area_code AS areaCode, " +
            "u.real_name AS userName, " +
            "an.anomaly_no AS anomalyNo, an.anomaly_type AS anomalyType, an.severity " +
            "FROM work_order wo " +
            "LEFT JOIN water_meter m ON m.id = wo.meter_id " +
            "LEFT JOIN area a ON a.id = wo.area_id " +
            "LEFT JOIN sys_user u ON u.id = wo.user_id " +
            "LEFT JOIN anomaly_record an ON an.id = wo.anomaly_id " +
            "WHERE wo.deleted = 0 " +
            "<if test='orderNo != null and orderNo != \"\"'> AND wo.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='orderType != null and orderType != \"\"'> AND wo.order_type = #{orderType} </if>" +
            "<if test='status != null'> AND wo.status = #{status} </if>" +
            "<if test='priority != null'> AND wo.priority = #{priority} </if>" +
            "<if test='handlerName != null and handlerName != \"\"'> AND wo.handler_name LIKE CONCAT('%', #{handlerName}, '%') </if>" +
            "<if test='meterNo != null and meterNo != \"\"'> AND m.meter_no LIKE CONCAT('%', #{meterNo}, '%') </if>" +
            "ORDER BY wo.priority ASC, wo.create_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<Map<String, Object>> selectJoinedPage(@Param("orderNo") String orderNo,
                                               @Param("orderType") String orderType,
                                               @Param("status") Integer status,
                                               @Param("priority") Integer priority,
                                               @Param("handlerName") String handlerName,
                                               @Param("meterNo") String meterNo,
                                               @Param("offset") long offset,
                                               @Param("pageSize") long pageSize);

    @Select("<script>" +
            "SELECT COUNT(*) FROM work_order wo " +
            "LEFT JOIN water_meter m ON m.id = wo.meter_id " +
            "WHERE wo.deleted = 0 " +
            "<if test='orderNo != null and orderNo != \"\"'> AND wo.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='orderType != null and orderType != \"\"'> AND wo.order_type = #{orderType} </if>" +
            "<if test='status != null'> AND wo.status = #{status} </if>" +
            "<if test='priority != null'> AND wo.priority = #{priority} </if>" +
            "<if test='handlerName != null and handlerName != \"\"'> AND wo.handler_name LIKE CONCAT('%', #{handlerName}, '%') </if>" +
            "<if test='meterNo != null and meterNo != \"\"'> AND m.meter_no LIKE CONCAT('%', #{meterNo}, '%') </if>" +
            "</script>")
    long countJoined(@Param("orderNo") String orderNo,
                     @Param("orderType") String orderType,
                     @Param("status") Integer status,
                     @Param("priority") Integer priority,
                     @Param("handlerName") String handlerName,
                     @Param("meterNo") String meterNo);

    @Select("SELECT wo.id, wo.order_no AS orderNo, wo.order_type AS orderType, " +
            "wo.anomaly_id AS anomalyId, wo.meter_id AS meterId, wo.user_id AS userId, " +
            "wo.area_id AS areaId, wo.title, wo.description, " +
            "wo.handler_id AS handlerId, wo.handler_name AS handlerName, " +
            "wo.priority, wo.status, wo.result, wo.rating, wo.feedback, wo.remark, " +
            "wo.create_time AS createTime, wo.dispatch_time AS dispatchTime, " +
            "wo.accept_time AS acceptTime, wo.complete_time AS completeTime, " +
            "m.meter_no AS meterNo, m.install_address AS installAddress, " +
            "a.area_name AS areaName, a.area_code AS areaCode, " +
            "u.real_name AS userName, " +
            "an.anomaly_no AS anomalyNo, an.anomaly_type AS anomalyType, an.severity " +
            "FROM work_order wo " +
            "LEFT JOIN water_meter m ON m.id = wo.meter_id " +
            "LEFT JOIN area a ON a.id = wo.area_id " +
            "LEFT JOIN sys_user u ON u.id = wo.user_id " +
            "LEFT JOIN anomaly_record an ON an.id = wo.anomaly_id " +
            "WHERE wo.deleted = 0 AND wo.id = #{id}")
    Map<String, Object> selectJoinedById(@Param("id") Long id);
}
