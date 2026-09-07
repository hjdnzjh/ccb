"""
异常检测智能体 (Anomaly Detection Agent)
核心能力: 三层异常检测(规则+统计+AI) + 分级预警 + 工单派发
自主感知: 感知用水数据波动、设备状态、异常信号
自主决策: 判定异常类型(偷水/漏水/故障)、评估严重程度、选择处置策略
自主执行: 发送预警、派发工单、跟踪处理闭环
"""
import time
import random
import numpy as np
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
from collections import deque
from loguru import logger

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.base_agent import (
    BaseAgent, AgentState, AgentCapability,
    PerceptionResult, DecisionResult, ExecutionResult, AgentMessage
)
from core.knowledge_base import KnowledgeBase


class AnomalyType:
    """异常类型"""
    LEAK = "leak"                     # 漏水
    THEFT = "theft"                   # 偷水
    METER_FAULT = "meter_fault"       # 水表故障
    SUDDEN_INCREASE = "sudden_increase"  # 用量突增
    ZERO_USAGE = "zero_usage"         # 长期零用量
    NIGHT_USAGE = "night_usage"       # 夜间异常用水
    REVERSE_READING = "reverse_reading"  # 读数倒退


class Severity:
    """严重程度"""
    CRITICAL = "critical"   # 紧急：大漏、偷水嫌疑
    HIGH = "high"           # 高：中漏、设备故障
    MEDIUM = "medium"       # 中：用量异常
    LOW = "low"             # 低：疑似异常


class AnomalyDetector:
    """三层异常检测引擎"""
    
    def __init__(self):
        self.history_window = 30  # 历史数据窗口（天）
        self.thresholds = {
            'sudden_increase_ratio': 3.0,
            'zero_usage_days': 30,
            'night_usage_ratio': 0.5,
            'gradual_increase_rate': 0.2
        }
    
    def detect(self, data: Dict) -> Dict:
        """
        综合检测
        
        Returns:
            {'anomalies': [...], 'severity': str, 'score': float}
        """
        anomalies = []
        
        # Layer 1: 规则引擎快速过滤
        rule_anomalies = self._rule_detection(data)
        anomalies.extend(rule_anomalies)
        
        # Layer 2: 统计模型检测
        stat_anomalies = self._statistical_detection(data)
        anomalies.extend(stat_anomalies)
        
        # Layer 3: AI深度模型（可选，模拟）
        ai_anomalies = self._ai_detection(data)
        anomalies.extend(ai_anomalies)
        
        # 去重并计算综合评分
        unique_anomalies = self._deduplicate(anomalies)
        severity = self._assess_severity(unique_anomalies)
        score = self._calculate_score(unique_anomalies)
        
        return {
            'anomalies': unique_anomalies,
            'severity': severity,
            'score': score,
            'detection_layers': {
                'rule': len(rule_anomalies),
                'stat': len(stat_anomalies),
                'ai': len(ai_anomalies)
            }
        }
    
    def _rule_detection(self, data: Dict) -> List[Dict]:
        """规则引擎检测"""
        anomalies = []
        current_usage = data.get('current_usage', 0)
        avg_usage = data.get('avg_usage', current_usage)
        zero_days = data.get('zero_usage_days', 0)
        night_usage = data.get('night_usage', 0)
        day_avg = data.get('day_avg_usage', avg_usage / 30)
        
        # 突增检测
        if avg_usage > 0 and current_usage > avg_usage * self.thresholds['sudden_increase_ratio']:
            anomalies.append({
                'type': AnomalyType.SUDDEN_INCREASE,
                'layer': 'rule',
                'description': f"用量突增{((current_usage/avg_usage - 1) * 100):.1f}%",
                'value': current_usage,
                'threshold': avg_usage * self.thresholds['sudden_increase_ratio'],
                'confidence': 0.85
            })
        
        # 零用量检测
        if zero_days >= self.thresholds['zero_usage_days']:
            anomalies.append({
                'type': AnomalyType.ZERO_USAGE,
                'layer': 'rule',
                'description': f"连续{zero_days}天零用量",
                'value': zero_days,
                'threshold': self.thresholds['zero_usage_days'],
                'confidence': 0.75
            })
        
        # 夜间异常用水
        if day_avg > 0 and night_usage > day_avg * self.thresholds['night_usage_ratio']:
            anomalies.append({
                'type': AnomalyType.NIGHT_USAGE,
                'layer': 'rule',
                'description': f"夜间用量异常: {night_usage:.1f}吨",
                'value': night_usage,
                'threshold': day_avg * self.thresholds['night_usage_ratio'],
                'confidence': 0.70
            })
        
        return anomalies
    
    def _statistical_detection(self, data: Dict) -> List[Dict]:
        """统计模型检测"""
        anomalies = []
        usage_history = data.get('usage_history', [])
        
        if len(usage_history) < 7:
            return anomalies
        
        # Z-Score异常检测
        values = np.array(usage_history)
        mean = np.mean(values)
        std = np.std(values)
        current = data.get('current_usage', 0)
        
        if std > 0:
            z_score = abs((current - mean) / std)
            if z_score > 3:
                anomalies.append({
                    'type': 'statistical_outlier',
                    'layer': 'stat',
                    'description': f"统计异常(Z-Score={z_score:.2f})",
                    'value': current,
                    'z_score': z_score,
                    'confidence': min(0.95, z_score / 5)
                })
        
        # 同比环比检测
        if len(usage_history) >= 30:
            last_month = usage_history[-30]
            if last_month > 0:
                yoy_change = (current - last_month) / last_month
                if abs(yoy_change) > 0.5:
                    anomalies.append({
                        'type': 'year_over_year_change',
                        'layer': 'stat',
                        'description': f"同比变化{yoy_change*100:.1f}%",
                        'value': yoy_change,
                        'confidence': min(0.80, abs(yoy_change))
                    })
        
        return anomalies
    
    def _ai_detection(self, data: Dict) -> List[Dict]:
        """AI深度模型检测（模拟）"""
        anomalies = []
        
        # 模拟AI模型推理
        ai_score = random.uniform(0, 1)
        
        # 如果随机分数高，模拟检测到漏水或偷水
        if ai_score > 0.85:
            anomaly_type = random.choice([AnomalyType.LEAK, AnomalyType.THEFT])
            anomalies.append({
                'type': anomaly_type,
                'layer': 'ai',
                'description': f"AI模型检测到{'漏水' if anomaly_type == AnomalyType.LEAK else '疑似偷水'}特征",
                'ai_score': ai_score,
                'confidence': ai_score,
                'model_version': 'v2.3.1'
            })
        
        return anomalies
    
    def _deduplicate(self, anomalies: List[Dict]) -> List[Dict]:
        """去重"""
        seen = set()
        unique = []
        for a in anomalies:
            key = f"{a.get('type')}:{a.get('layer')}"
            if key not in seen:
                seen.add(key)
                unique.append(a)
        return unique
    
    def _assess_severity(self, anomalies: List[Dict]) -> str:
        """评估严重程度"""
        if not anomalies:
            return Severity.LOW
        
        # 有AI检测到的漏水或偷水
        if any(a.get('layer') == 'ai' and a.get('type') in [AnomalyType.LEAK, AnomalyType.THEFT] 
               for a in anomalies):
            return Severity.CRITICAL
        
        # 有多个异常或高置信度异常
        if len(anomalies) >= 3:
            return Severity.HIGH
        
        max_confidence = max(a.get('confidence', 0) for a in anomalies)
        if max_confidence > 0.9:
            return Severity.HIGH
        elif max_confidence > 0.7:
            return Severity.MEDIUM
        
        return Severity.LOW
    
    def _calculate_score(self, anomalies: List[Dict]) -> float:
        """计算综合异常分数"""
        if not anomalies:
            return 0.0
        return min(1.0, sum(a.get('confidence', 0) for a in anomalies) / len(anomalies))


class AnomalyAgent(BaseAgent):
    """
    异常检测智能体
    
    感知能力:
    - 感知用水数据实时流
    - 感知设备状态变化
    - 感知其他智能体的异常通知
    
    决策能力:
    - 三层异常检测（规则+统计+AI）
    - 异常类型判定
    - 严重程度评估
    
    执行能力:
    - 分级预警发送
    - 工单自动派发
    - 处理结果跟踪
    """

    def __init__(self, knowledge_base: KnowledgeBase = None):
        super().__init__(
            agent_id="anomaly_agent",
            agent_name="异常检测智能体",
            description="负责用水异常检测、预警通知和工单派发"
        )
        self.capabilities = [
            AgentCapability.PERCEIVE,
            AgentCapability.DECIDE,
            AgentCapability.EXECUTE,
            AgentCapability.LEARN,
            AgentCapability.COLLABORATE
        ]
        
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.detector = AnomalyDetector()
        
        # 异常记录
        self.detected_anomalies: List[Dict] = []
        self.active_alerts: List[Dict] = []
        self.work_orders: List[Dict] = []

    def _on_start(self):
        logger.info("异常检测智能体: 初始化完成，检测引擎已就绪")

    # ==================== 感知 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """
        感知异常数据
        
        模式:
        1. realtime: 实时数据流检测
        2. batch: 批量数据扫描
        3. event: 响应其他智能体的异常事件
        """
        mode = context.get('mode', 'realtime')
        
        if mode == 'realtime':
            return self._perceive_realtime(context)
        elif mode == 'batch':
            return self._perceive_batch(context)
        elif mode == 'event':
            return self._perceive_event(context)
        else:
            return PerceptionResult(confidence=0.0, metadata={'error': f'未知模式: {mode}'})

    def _perceive_realtime(self, context: Dict) -> PerceptionResult:
        """实时数据感知"""
        meter_id = context.get('meter_id', 'unknown')
        current_usage = context.get('current_usage', 0)
        usage_history = context.get('usage_history', [])
        zero_usage_days = context.get('zero_usage_days', 0)
        
        # 计算统计量
        avg_usage = np.mean(usage_history) if usage_history else current_usage
        day_avg = avg_usage / 30 if avg_usage > 0 else 0
        
        perceived_data = {
            'meter_id': meter_id,
            'current_usage': current_usage,
            'avg_usage': avg_usage,
            'usage_history': usage_history,
            'zero_usage_days': zero_usage_days,
            'night_usage': context.get('night_usage', 0),
            'day_avg_usage': day_avg,
            'device_status': context.get('device_status', 'normal'),
            'timestamp': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95,
            source='realtime_stream',
            metadata={'data_points': len(usage_history)}
        )

    def _perceive_batch(self, context: Dict) -> PerceptionResult:
        """批量数据感知"""
        meters_data = context.get('meters_data', [])
        
        perceived_data = {
            'mode': 'batch',
            'meters_data': meters_data,
            'total_meters': len(meters_data),
            'timestamp': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.90,
            source='batch_scan',
            metadata={'scan_range': context.get('scan_range', 'all')}
        )

    def _perceive_event(self, context: Dict) -> PerceptionResult:
        """事件感知"""
        event_type = context.get('event_type', 'unknown')
        
        return PerceptionResult(
            data={
                'mode': 'event',
                'event_type': event_type,
                'event_data': context.get('event_data', {}),
                'source_agent': context.get('source_agent', 'unknown'),
                'timestamp': datetime.now().isoformat()
            },
            confidence=0.85,
            source='agent_event',
            metadata={'priority': context.get('priority', 5)}
        )

    # ==================== 决策 ====================

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """
        异常检测决策
        """
        data = perception.data or {}
        mode = data.get('mode', 'realtime')
        
        if mode == 'batch':
            return self._decide_batch(data)
        elif mode == 'event':
            return self._decide_event(data)
        
        # 调用三层检测引擎
        detection_result = self.detector.detect(data)
        
        anomalies = detection_result.get('anomalies', [])
        severity = detection_result.get('severity', Severity.LOW)
        score = detection_result.get('score', 0)
        
        if not anomalies:
            return DecisionResult(
                action="no_anomaly",
                params={'meter_id': data.get('meter_id')},
                confidence=1.0,
                reasoning="未检测到异常"
            )
        
        # 根据严重程度决定处置策略
        action, params = self._decide_action(data, anomalies, severity, score)
        
        reasoning = f"检测到{len(anomalies)}个异常, 严重程度: {severity}, 分数: {score:.2f}"
        reasoning += "; " + "; ".join([a.get('description', '') for a in anomalies])
        
        return DecisionResult(
            action=action,
            params=params,
            confidence=score,
            reasoning=reasoning,
            alternatives=[
                {'action': 'send_alert', 'description': '发送预警'},
                {'action': 'create_work_order', 'description': '创建工单'},
                {'action': 'notify_human', 'description': '通知人工处理'}
            ]
        )

    def _decide_action(self, data: Dict, anomalies: List[Dict], 
                       severity: str, score: float) -> Tuple[str, Dict]:
        """决定处置动作"""
        meter_id = data.get('meter_id', 'unknown')
        
        base_params = {
            'meter_id': meter_id,
            'anomalies': anomalies,
            'severity': severity,
            'score': score,
            'timestamp': datetime.now().isoformat()
        }
        
        if severity == Severity.CRITICAL:
            # 紧急：立即停水 + 派单 + 高优先级通知
            return ("critical_response", {
                **base_params,
                'immediate_action': 'potential_water_shutoff',
                'work_order_type': 'emergency',
                'notify_level': 'critical'
            })
        elif severity == Severity.HIGH:
            # 高：派单 + 预警
            return ("high_priority_alert", {
                **base_params,
                'work_order_type': 'urgent',
                'notify_level': 'high'
            })
        elif severity == Severity.MEDIUM:
            # 中：发送预警 + 记录观察
            return ("medium_alert", {
                **base_params,
                'work_order_type': 'normal',
                'notify_level': 'medium'
            })
        else:
            # 低：记录观察
            return ("log_observation", {
                **base_params,
                'notify_level': 'low'
            })

    def _decide_batch(self, data: Dict) -> DecisionResult:
        """批量检测决策"""
        meters_data = data.get('meters_data', [])
        results = []
        critical_count = 0
        
        for meter in meters_data[:20]:  # 限制演示数量
            detection = self.detector.detect(meter)
            if detection['anomalies']:
                results.append({
                    'meter_id': meter.get('meter_id'),
                    **detection
                })
                if detection['severity'] == Severity.CRITICAL:
                    critical_count += 1
        
        return DecisionResult(
            action="batch_report",
            params={
                'anomaly_count': len(results),
                'critical_count': critical_count,
                'results': results
            },
            confidence=0.90,
            reasoning=f"批量扫描完成: 检测到{len(results)}个异常, 其中紧急{critical_count}个"
        )

    def _decide_event(self, data: Dict) -> DecisionResult:
        """事件决策"""
        event_type = data.get('event_type')
        event_data = data.get('event_data', {})
        
        return DecisionResult(
            action="process_event",
            params={
                'event_type': event_type,
                'event_data': event_data,
                'source_agent': data.get('source_agent')
            },
            confidence=0.85,
            reasoning=f"处理来自{data.get('source_agent')}的事件: {event_type}"
        )

    # ==================== 执行 ====================

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """执行异常处置"""
        action = decision.action
        params = decision.params
        
        if action == "no_anomaly":
            return ExecutionResult(success=True, message="无异常，正常")
        elif action == "critical_response":
            return self._execute_critical(params)
        elif action == "high_priority_alert":
            return self._execute_high_alert(params)
        elif action == "medium_alert":
            return self._execute_medium_alert(params)
        elif action == "log_observation":
            return self._execute_log(params)
        elif action == "batch_report":
            return self._execute_batch_report(params)
        elif action == "process_event":
            return self._execute_process_event(params)
        else:
            return ExecutionResult(success=False, message=f"未知动作: {action}")

    def _execute_critical(self, params: Dict) -> ExecutionResult:
        """紧急异常处置"""
        meter_id = params.get('meter_id')
        anomalies = params.get('anomalies', [])
        
        # 记录异常
        anomaly_record = {
            'anomaly_id': f"ANOM-{datetime.now().strftime('%Y%m%d%H%M%S')}",
            'meter_id': meter_id,
            'anomalies': anomalies,
            'severity': Severity.CRITICAL,
            'status': 'active',
            **params
        }
        self.detected_anomalies.append(anomaly_record)
        
        # 创建紧急工单
        work_order = self._create_work_order(meter_id, 'emergency', anomalies)
        
        # 发送高优先级预警
        alert = self._send_alert(meter_id, Severity.CRITICAL, anomalies, work_order.get('order_id'))
        
        logger.warning(f"异常检测智能体: 紧急异常 [{meter_id}] - {anomalies}")
        
        return ExecutionResult(
            success=True,
            data={'anomaly_record': anomaly_record, 'work_order': work_order, 'alert': alert},
            message=f"紧急异常已处理: {meter_id}, 工单: {work_order.get('order_id')}",
            next_actions=['notify_maintenance', 'potential_water_shutoff']
        )

    def _execute_high_alert(self, params: Dict) -> ExecutionResult:
        """高优先级预警"""
        meter_id = params.get('meter_id')
        anomalies = params.get('anomalies', [])
        
        anomaly_record = {
            'anomaly_id': f"ANOM-{datetime.now().strftime('%Y%m%d%H%M%S')}",
            'meter_id': meter_id,
            'anomalies': anomalies,
            'severity': Severity.HIGH,
            'status': 'active',
            **params
        }
        self.detected_anomalies.append(anomaly_record)
        
        work_order = self._create_work_order(meter_id, 'urgent', anomalies)
        alert = self._send_alert(meter_id, Severity.HIGH, anomalies, work_order.get('order_id'))
        
        logger.warning(f"异常检测智能体: 高优先级异常 [{meter_id}]")
        
        return ExecutionResult(
            success=True,
            data={'anomaly_record': anomaly_record, 'work_order': work_order, 'alert': alert},
            message=f"高优先级异常已处理: {meter_id}"
        )

    def _execute_medium_alert(self, params: Dict) -> ExecutionResult:
        """中等预警"""
        meter_id = params.get('meter_id')
        anomalies = params.get('anomalies', [])
        
        alert = self._send_alert(meter_id, Severity.MEDIUM, anomalies)
        
        logger.info(f"异常检测智能体: 中等异常预警 [{meter_id}]")
        
        return ExecutionResult(
            success=True,
            data={'alert': alert},
            message=f"中等异常预警已发送: {meter_id}"
        )

    def _execute_log(self, params: Dict) -> ExecutionResult:
        """记录观察"""
        meter_id = params.get('meter_id')
        
        logger.info(f"异常检测智能体: 低优先级观察 [{meter_id}]")
        
        return ExecutionResult(
            success=True,
            message=f"观察记录: {meter_id}"
        )

    def _execute_batch_report(self, params: Dict) -> ExecutionResult:
        """批量报告"""
        results = params.get('results', [])
        
        logger.info(f"异常检测智能体: 批量扫描报告 - {len(results)}个异常")
        
        return ExecutionResult(
            success=True,
            data=params,
            message=f"批量报告: {params.get('anomaly_count')}个异常"
        )

    def _execute_process_event(self, params: Dict) -> ExecutionResult:
        """处理事件"""
        event_type = params.get('event_type')
        event_data = params.get('event_data', {})
        
        logger.info(f"异常检测智能体: 处理事件 [{event_type}]")
        
        return ExecutionResult(
            success=True,
            data=params,
            message=f"事件已处理: {event_type}"
        )

    # ==================== 辅助方法 ====================

    def _create_work_order(self, meter_id: str, order_type: str, anomalies: List[Dict]) -> Dict:
        """创建工单"""
        order_id = f"WO-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        work_order = {
            'order_id': order_id,
            'meter_id': meter_id,
            'type': order_type,
            'anomalies': anomalies,
            'status': 'pending',
            'created_at': datetime.now().isoformat(),
            'assigned_to': None,
            'priority': 1 if order_type == 'emergency' else 2 if order_type == 'urgent' else 3
        }
        
        self.work_orders.append(work_order)
        self.knowledge_base.set_fact(f"work_order:{order_id}", work_order, self.agent_id)
        
        logger.info(f"异常检测智能体: 创建工单 [{order_id}]")
        
        return work_order

    def _send_alert(self, meter_id: str, severity: str, 
                    anomalies: List[Dict], work_order_id: str = None) -> Dict:
        """发送预警"""
        alert_id = f"ALERT-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        alert = {
            'alert_id': alert_id,
            'meter_id': meter_id,
            'severity': severity,
            'anomalies': anomalies,
            'work_order_id': work_order_id,
            'channels': ['sms', 'app', 'dashboard'] if severity in [Severity.CRITICAL, Severity.HIGH] else ['dashboard'],
            'sent_at': datetime.now().isoformat(),
            'status': 'sent'
        }
        
        self.active_alerts.append(alert)
        
        return alert

    def get_anomaly_stats(self) -> Dict:
        """获取异常统计"""
        return {
            'total_detected': len(self.detected_anomalies),
            'active_alerts': len(self.active_alerts),
            'open_work_orders': len([w for w in self.work_orders if w['status'] == 'pending']),
            'by_severity': {
                'critical': len([a for a in self.detected_anomalies if a.get('severity') == Severity.CRITICAL]),
                'high': len([a for a in self.detected_anomalies if a.get('severity') == Severity.HIGH]),
                'medium': len([a for a in self.detected_anomalies if a.get('severity') == Severity.MEDIUM]),
                'low': len([a for a in self.detected_anomalies if a.get('severity') == Severity.LOW])
            }
        }
