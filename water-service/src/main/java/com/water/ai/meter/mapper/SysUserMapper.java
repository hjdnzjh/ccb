package com.water.ai.meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.water.ai.meter.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(String username);

    /**
     * 根据手机号查询
     */
    @Select("SELECT * FROM sys_user WHERE phone = #{phone} AND deleted = 0")
    SysUser selectByPhone(String phone);

    /**
     * 统计各类型用户数量
     */
    @Select("SELECT user_type, COUNT(*) as count FROM sys_user WHERE deleted = 0 GROUP BY user_type")
    List<Map<String, Object>> countByUserType();

    /**
     * 统计各信用等级用户数量
     */
    @Select("SELECT credit_level, COUNT(*) as count FROM sys_user WHERE deleted = 0 GROUP BY credit_level")
    List<Map<String, Object>> countByCreditLevel();

    /**
     * 查询欠费用户列表
     */
    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN bill b ON u.id = b.user_id " +
            "WHERE b.status IN (0, 2) AND b.due_date < NOW() " +
            "AND u.deleted = 0 " +
            "GROUP BY u.id")
    IPage<SysUser> selectOverdueUsers(Page<SysUser> page);

    /**
     * 更新用户余额
     */
    @Select("UPDATE sys_user SET balance = balance + #{amount} WHERE id = #{userId}")
    int updateBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}