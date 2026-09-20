package com.water.ai.meter.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AnomalyOperations {
 private final JdbcTemplate jdbc;
 public AnomalyOperations(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Map<String,Object> list(int page,int size,String meterNo,Integer status,String severity){
  if(page<1 || size<1 || size>100)throw new IllegalArgumentException("分页范围无效");
  String where=" FROM anomaly_record a LEFT JOIN water_meter m ON m.id=a.meter_id WHERE a.deleted=0";List<Object> args=new ArrayList<>();
  if(meterNo!=null && !meterNo.isBlank()){where+=" AND m.meter_no LIKE ?";args.add("%"+meterNo.trim()+"%");}
  if(status!=null){where+=" AND a.status=?";args.add(status);}if(severity!=null && !severity.isBlank()){where+=" AND a.severity=?";args.add(severity);}
  long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
  return Map.of("total",total,"records",jdbc.queryForList("SELECT a.*,m.meter_no"+where+" ORDER BY FIELD(a.status,0,1,2,3),FIELD(a.severity,'critical','high','medium','low'),a.id DESC LIMIT ? OFFSET ?",args.toArray()));
 }
 public Map<String,Object> detail(long id){var rows=jdbc.queryForList("SELECT a.*,m.meter_no,w.order_no,w.status order_status FROM anomaly_record a LEFT JOIN water_meter m ON m.id=a.meter_id LEFT JOIN work_order w ON w.id=a.work_order_id AND w.deleted=0 WHERE a.id=? AND a.deleted=0",id);if(rows.isEmpty())throw new IllegalArgumentException("异常不存在");return rows.get(0);}
 private Map<String,Object> lock(long id){var rows=jdbc.queryForList("SELECT * FROM anomaly_record WHERE id=? AND deleted=0 FOR UPDATE",id);if(rows.isEmpty())throw new IllegalArgumentException("异常不存在");return rows.get(0);}
 @Transactional public long workOrder(long id){
  var a=lock(id);if(a.get("work_order_id")!=null)return ((Number)a.get("work_order_id")).longValue();
  if(((Number)a.get("status")).intValue()>1)throw new IllegalArgumentException("已结案异常不能再生成工单");
  String no="WO-"+UUID.randomUUID().toString().replace("-","");int priority="critical".equals(a.get("severity"))?1:"high".equals(a.get("severity"))?2:3;
  jdbc.update("INSERT INTO work_order(order_no,anomaly_id,meter_id,user_id,area_id,title,description,priority,order_type,status) SELECT ?,a.id,a.meter_id,a.user_id,m.area_id,?,a.description,?,?,0 FROM anomaly_record a LEFT JOIN water_meter m ON m.id=a.meter_id WHERE a.id=?",no,"异常核查："+a.get("anomaly_no"),priority,priority==1?"emergency":priority==2?"urgent":"normal",id);
  long order=jdbc.queryForObject("SELECT id FROM work_order WHERE order_no=?",Long.class,no);
  jdbc.update("UPDATE anomaly_record SET work_order_id=?,status=1 WHERE id=?",order,id);return order;
 }
 @Transactional public void resolve(long id,int status,String note,String actor){
  if((status!=2 && status!=3)||note==null||note.isBlank()||note.length()>500)throw new IllegalArgumentException("请选择已处理或关闭，并填写1至500字核查结论");
  var a=lock(id);if(((Number)a.get("status")).intValue()>1)throw new IllegalArgumentException("异常已经结案，不能重复修改处置结论");
  if(a.get("work_order_id")!=null){Integer open=jdbc.queryForObject("SELECT COUNT(*) FROM work_order WHERE id=? AND deleted=0 AND status<3",Integer.class,a.get("work_order_id"));if(open>0)throw new IllegalArgumentException("关联工单尚未结案，请先在工单管理中完成处置");}
  jdbc.update("UPDATE anomaly_record SET status=?,handle_result=?,handler=?,handled_time=NOW() WHERE id=?",status,note.trim(),actor,id);
 }
}
