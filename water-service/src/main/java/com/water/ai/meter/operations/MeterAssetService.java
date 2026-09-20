package com.water.ai.meter.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class MeterAssetService {
 private final JdbcTemplate jdbc;
 public MeterAssetService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Input(String meterNo,String meterType,String commType,Long userId,Long areaId,String installAddress,String manufacturer,Integer status){}
 public Map<String,Object> options(){return Map.of("users",jdbc.queryForList("SELECT id,username,real_name FROM sys_user WHERE deleted=0 AND status=0 ORDER BY id"),"areas",jdbc.queryForList("SELECT id,area_name FROM area WHERE deleted=0 ORDER BY id"));}
 @Transactional public long save(Long id,Input v){
  if(v==null || v.meterNo()==null || !v.meterNo().matches("[A-Za-z0-9_-]{1,50}") || v.meterType()==null || !Set.of("digital","pointer","wheel").contains(v.meterType()) || v.commType()==null || !Set.of("NB-IoT","LoRa","4G","wired").contains(v.commType()) || v.status()==null || v.status()<0 || v.status()>3 || v.userId()==null || v.areaId()==null)throw new IllegalArgumentException("请填写有效表号、表类型、通信方式、用户、区域与状态");
  if((v.installAddress()!=null && v.installAddress().length()>255)||(v.manufacturer()!=null && v.manufacturer().length()>100))throw new IllegalArgumentException("地址或厂商名称过长");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND deleted=0 AND status=0",Integer.class,v.userId())!=1 || jdbc.queryForObject("SELECT COUNT(*) FROM area WHERE id=? AND deleted=0",Integer.class,v.areaId())!=1)throw new IllegalArgumentException("用户或区域不存在/不可用");
  if(id==null){
   jdbc.update("INSERT INTO water_meter(meter_no,meter_type,comm_type,user_id,area_id,install_address,manufacturer,status,current_reading,last_reading) VALUES(?,?,?,?,?,?,?,?,0,0)",v.meterNo(),v.meterType(),v.commType(),v.userId(),v.areaId(),v.installAddress(),v.manufacturer(),v.status());
   return jdbc.queryForObject("SELECT id FROM water_meter WHERE meter_no=?",Long.class,v.meterNo());
  }
  var rows=jdbc.queryForList("SELECT * FROM water_meter WHERE id=? AND deleted=0 FOR UPDATE",id);
  if(rows.isEmpty())throw new IllegalArgumentException("水表不存在");
  var existing=rows.get(0);
  if(!v.meterNo().equals(existing.get("meter_no")) || !Objects.equals(v.userId(),existing.get("user_id")==null?null:((Number)existing.get("user_id")).longValue())){
   long history=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM meter_reading WHERE meter_id=?)+(SELECT COUNT(*) FROM bill WHERE meter_id=?)+(SELECT COUNT(*) FROM automation_plan_meter WHERE meter_id=?)+(SELECT COUNT(*) FROM tariff_account_meter WHERE meter_id=?)",Long.class,id,id,id,id);
   if(history>0)throw new IllegalArgumentException("已有抄表、账单或采集计划的水表不能直接改表号或转户，请停用后另建档案");
  }
  jdbc.update("UPDATE water_meter SET meter_no=?,meter_type=?,comm_type=?,user_id=?,area_id=?,install_address=?,manufacturer=?,status=? WHERE id=?",v.meterNo(),v.meterType(),v.commType(),v.userId(),v.areaId(),v.installAddress(),v.manufacturer(),v.status(),id);
  return id;
 }
}
