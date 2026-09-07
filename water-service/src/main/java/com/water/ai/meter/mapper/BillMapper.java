package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.entity.Bill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账单Mapper接口
 */
@Mapper
public interface BillMapper extends BaseMapper<Bill> {

    /**
     * 根据账单编号查询
     */
    @Select("SELECT * FROM bill WHERE bill_no = #{billNo} AND deleted = 0")
    Bill selectByBillNo(String billNo);

    /**
     * 根据用户ID查询账单列表
     */
    @Select("SELECT * FROM bill WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<Bill> selectByUserId(Long userId);

    /**
     * 查询用户未支付账单
     */
    @Select("SELECT * FROM bill WHERE user_id = #{userId} AND status = 0 AND deleted = 0")
    List<Bill> selectUnpaidByUserId(Long userId);

    /**
     * 查询逾期账单
     */
    @Select("SELECT * FROM bill WHERE status = 0 AND due_date < NOW() AND deleted = 0")
    List<Bill> selectOverdueBills();

    /**
     * 统计账单状态分布
     */
    @Select("SELECT status, COUNT(*) as count FROM bill WHERE deleted = 0 " +
            "AND create_time >= #{startDate} GROUP BY status")
    List<Map<String, Object>> countByStatus(LocalDateTime startDate);

    /**
     * 统计收入
     */
    @Select("SELECT SUM(total_amount) as total, SUM(paid_amount) as paid " +
            "FROM bill WHERE deleted = 0 AND create_time >= #{startDate}")
    Map<String, BigDecimal> sumAmount(LocalDateTime startDate);

    /**
     * 按账期统计
     */
    @Select("SELECT bill_period, SUM(total_amount) as total, COUNT(*) as count " +
            "FROM bill WHERE deleted = 0 GROUP BY bill_period ORDER BY bill_period DESC")
    List<Map<String, Object>> sumByPeriod();

    /**
     * 按用户类型统计收入
     */
    @Select("SELECT b.price_type, SUM(b.total_amount) as total " +
            "FROM bill b WHERE b.deleted = 0 AND b.create_time >= #{startDate} " +
            "GROUP BY b.price_type")
    List<Map<String, Object>> sumByPriceType(LocalDateTime startDate);

    /**
     * 更新账单状态
     */
    @Update("UPDATE bill SET status = #{status}, paid_amount = #{paidAmount}, " +
            "paid_time = #{paidTime}, pay_method = #{payMethod}, trade_no = #{tradeNo}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updatePayment(@Param("id") Long id, @Param("status") Integer status,
                      @Param("paidAmount") BigDecimal paidAmount,
                      @Param("paidTime") LocalDateTime paidTime,
                      @Param("payMethod") String payMethod,
                      @Param("tradeNo") String tradeNo);

    /**
     * 计算收费率
     */
    @Select("SELECT COUNT(*) as total, SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as paid " +
            "FROM bill WHERE deleted = 0 AND create_time >= #{startDate}")
    Map<String, Object> calculateCollectionRate(LocalDateTime startDate);

    /**
     * 统计欠费金额排行
     */
    @Select("SELECT u.id, u.username, u.real_name, u.phone, " +
            "SUM(b.total_amount - COALESCE(b.paid_amount, 0)) as overdue_amount " +
            "FROM bill b INNER JOIN sys_user u ON b.user_id = u.id " +
            "WHERE b.status IN (0, 2) AND b.due_date < NOW() " +
            "AND b.deleted = 0 AND u.deleted = 0 " +
            "GROUP BY u.id ORDER BY overdue_amount DESC LIMIT #{limit}")
    List<Map<String, Object>> topOverdueUsers(int limit);

    /** 管理端账单分页（关联用户姓名与手机号） */
    @Select("<script>" +
            "SELECT b.id, b.bill_no AS billNo, b.user_id AS userId, b.meter_id AS meterId, " +
            "b.bill_period AS billPeriod, b.usage_amount AS usageAmount, b.water_fee AS waterFee, " +
            "b.sewage_fee AS sewageFee, b.total_amount AS totalAmount, b.paid_amount AS paidAmount, " +
            "b.status, b.due_date AS dueDate, b.paid_time AS paidTime, b.pay_method AS payMethod, " +
            "COALESCE(NULLIF(u.real_name, ''), u.username) AS userName, u.phone " +
            "FROM bill b LEFT JOIN sys_user u ON u.id = b.user_id AND u.deleted = 0 " +
            "WHERE b.deleted = 0 " +
            "<if test='billNo != null'>AND b.bill_no LIKE CONCAT('%', #{billNo}, '%') </if>" +
            "<if test='userName != null'>AND (u.real_name LIKE CONCAT('%', #{userName}, '%') " +
            "OR u.username LIKE CONCAT('%', #{userName}, '%') OR u.phone LIKE CONCAT('%', #{userName}, '%')) </if>" +
            "<if test='status != null'>AND b.status = #{status} </if>" +
            "<if test='billPeriod != null'>AND b.bill_period = #{billPeriod} </if>" +
            "ORDER BY b.create_time DESC, b.id DESC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<Map<String, Object>> selectAdminPage(
            @Param("billNo") String billNo,
            @Param("userName") String userName,
            @Param("status") Integer status,
            @Param("billPeriod") String billPeriod,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    @Select("<script>" +
            "SELECT COUNT(*) FROM bill b LEFT JOIN sys_user u ON u.id = b.user_id AND u.deleted = 0 " +
            "WHERE b.deleted = 0 " +
            "<if test='billNo != null'>AND b.bill_no LIKE CONCAT('%', #{billNo}, '%') </if>" +
            "<if test='userName != null'>AND (u.real_name LIKE CONCAT('%', #{userName}, '%') " +
            "OR u.username LIKE CONCAT('%', #{userName}, '%') OR u.phone LIKE CONCAT('%', #{userName}, '%')) </if>" +
            "<if test='status != null'>AND b.status = #{status} </if>" +
            "<if test='billPeriod != null'>AND b.bill_period = #{billPeriod} </if>" +
            "</script>")
    long countAdminPage(
            @Param("billNo") String billNo,
            @Param("userName") String userName,
            @Param("status") Integer status,
            @Param("billPeriod") String billPeriod);

    @Select("<script>" +
            "SELECT COALESCE(SUM(b.total_amount), 0) AS totalAmount, " +
            "COALESCE(SUM(b.paid_amount), 0) AS paidAmount, " +
            "COALESCE(SUM(CASE WHEN b.status IN (0,2,3) THEN b.total_amount - COALESCE(b.paid_amount,0) ELSE 0 END), 0) AS overdueAmount, " +
            "CASE WHEN COALESCE(SUM(b.total_amount),0) = 0 THEN 0 " +
            "ELSE ROUND(COALESCE(SUM(b.paid_amount),0) / SUM(b.total_amount) * 100, 1) END AS collectionRate " +
            "FROM bill b LEFT JOIN sys_user u ON u.id = b.user_id AND u.deleted = 0 WHERE b.deleted = 0 " +
            "<if test='billNo != null'>AND b.bill_no LIKE CONCAT('%', #{billNo}, '%') </if>" +
            "<if test='userName != null'>AND (u.real_name LIKE CONCAT('%', #{userName}, '%') " +
            "OR u.username LIKE CONCAT('%', #{userName}, '%') OR u.phone LIKE CONCAT('%', #{userName}, '%')) </if>" +
            "<if test='status != null'>AND b.status = #{status} </if>" +
            "<if test='billPeriod != null'>AND b.bill_period = #{billPeriod} </if>" +
            "</script>")
    Map<String, Object> sumAdminPage(
            @Param("billNo") String billNo,
            @Param("userName") String userName,
            @Param("status") Integer status,
            @Param("billPeriod") String billPeriod);
}
