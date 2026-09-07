"""
抄表智能体 (Meter Reading Agent)
核心能力: 基于杭州 CSV 传感数据的远程抄表 + 读数校验 + 自动录入
（图像 OCR 为遗留可选路径，默认不使用）
"""
import time
import random
import hashlib
from typing import Dict, List, Optional
from datetime import datetime, timedelta
from loguru import logger

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.base_agent import (
    BaseAgent, AgentState, AgentCapability, 
    PerceptionResult, DecisionResult, ExecutionResult, AgentMessage
)
from core.knowledge_base import KnowledgeBase
from core.meter_data_store import get_meter_store


class OCRModel:
    """遗留图像 OCR 占位（默认不启用）"""

    def __init__(self, model_dir: str = ""):
        self.model_dir = model_dir
        self.loaded = False
        logger.info("OCR模型: 未启用（当前使用 CSV 传感抄表）")

    def recognize(self, image_data: bytes) -> Dict:
        image_hash = hashlib.md5(image_data).hexdigest() if isinstance(image_data, bytes) else str(hash(image_data))
        random.seed(image_hash)
        confidence = random.uniform(0.70, 0.99)
        reading = round(random.uniform(100, 99999), 1)
        return {
            'reading': reading,
            'confidence': confidence,
            'digits': [int(d) for d in str(int(reading))],
            'meter_type': 'digital',
            'scenario': 'legacy',
            'processing_time': 0.1
        }


class MeterReadingAgent(BaseAgent):
    """
    抄表智能体（CSV 传感数据驱动）

    感知:
    - remote / csv: 从 hangzhou_water_meter_1000.csv 取真实传感样本
    - schedule: 按区批量拉取 CSV 水表
    - image: 遗留路径（仅兼容旧接口）
    """

    def __init__(self, knowledge_base: KnowledgeBase = None):
        super().__init__(
            agent_id="meter_reading_agent",
            agent_name="抄表智能体",
            description="基于杭州 CSV 传感数据的远程抄表、读数校验与自动录入"
        )
        self.capabilities = [
            AgentCapability.PERCEIVE,
            AgentCapability.DECIDE,
            AgentCapability.EXECUTE,
            AgentCapability.LEARN
        ]
        self.ocr_model = OCRModel()
        self.meter_store = get_meter_store()
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.reading_tasks: List[Dict] = []
        self.completed_readings: List[Dict] = []
        self.pending_reviews: List[Dict] = []
        self.confidence_threshold = 0.95
        self.review_threshold = 0.80

    def _on_start(self):
        stats = self.meter_store.stats()
        logger.info(
            f"抄表智能体: 已加载 CSV 传感数据 {stats['total']} 条, "
            f"异常 {stats['alarms']} 条, 区县 {len(stats['districts'])} 个"
        )

    # ==================== 感知 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """
        感知水表数据

        模式:
        1. remote / csv: CSV 传感远程抄表（默认）
        2. schedule: 按区批量 CSV 抄表
        3. image: 遗留图像路径
        """
        mode = context.get('mode', 'remote')
        if mode in ('remote', 'csv', 'sensor'):
            return self._perceive_remote(context)
        if mode == 'schedule':
            return self._perceive_schedule(context)
        if mode == 'image':
            return self._perceive_image(context)
        return PerceptionResult(
            confidence=0.0,
            metadata={'error': f'不支持的感知模式: {mode}'}
        )

    def _perceive_image(self, context: Dict) -> PerceptionResult:
        """遗留图像抄表（非默认）"""
        image_data = context.get('image_data', b'simulated_image')
        meter_id = context.get('meter_id', 'unknown')
        logger.info(f"抄表智能体: 图像抄表(遗留), 水表ID: {meter_id}")
        ocr_result = self.ocr_model.recognize(image_data)
        last_reading = self._get_last_reading(meter_id)
        perceived_data = {
            'meter_id': meter_id,
            'mode': 'image',
            'reading': ocr_result['reading'],
            'confidence': ocr_result['confidence'],
            'meter_type': ocr_result['meter_type'],
            'last_reading': last_reading,
            'ocr_raw': ocr_result,
            'timestamp': datetime.now().isoformat()
        }
        return PerceptionResult(
            data=perceived_data,
            confidence=ocr_result['confidence'],
            source='ocr_legacy',
            metadata={'processing_time': ocr_result['processing_time']}
        )

    def _perceive_remote(self, context: Dict) -> PerceptionResult:
        """远程/传感抄表：优先使用 CSV 真实样本"""
        meter_id = context.get('meter_id')
        row = None
        if meter_id:
            row = self.meter_store.get(meter_id)
        if row is None:
            # 未指定或找不到时，取 CSV 中第一条样本
            samples = self.meter_store.sample(1)
            if not samples:
                return PerceptionResult(confidence=0.0, metadata={'error': 'CSV 无可用水表数据'})
            row = samples[0]
            meter_id = row['meter_id']

        # 允许调用方覆盖读数；否则用 CSV
        reading = context.get('reading')
        if reading in (None, 0, '0', ''):
            reading = row['reading']
        else:
            reading = float(reading)

        signal_strength = context.get('signal_strength') or row['signal_strength']
        confidence = float(context.get('confidence') or row['confidence'])
        last_reading = self._get_last_reading(meter_id) or row['last_reading']

        logger.info(
            f"抄表智能体: CSV传感抄表, 水表ID: {meter_id}, "
            f"读数: {reading}, 用量: {row['water_usage']}, 区: {row['district']}"
        )

        perceived_data = {
            'meter_id': meter_id,
            'mode': 'remote',
            'source': 'csv',
            'reading': reading,
            'last_reading': last_reading,
            'confidence': confidence,
            'signal_strength': signal_strength,
            'water_usage': row['water_usage'],
            'flow_rate': row['flow_rate'],
            'pressure': row['pressure'],
            'temperature': row['temperature'],
            'status': row['status'],
            'alarm': row['alarm'],
            'district': row['district'],
            'community': row['community'],
            'sample_time': row['timestamp'],
            'timestamp': datetime.now().isoformat()
        }
        return PerceptionResult(
            data=perceived_data,
            confidence=confidence,
            source='csv_sensor',
            metadata={'protocol': context.get('protocol', 'NB-IoT'), 'csv': True}
        )

    def _perceive_schedule(self, context: Dict) -> PerceptionResult:
        """按区批量拉取 CSV 水表"""
        area_id = context.get('area_id') or context.get('district') or 'all'
        meter_count = int(context.get('meter_count', 10))

        # 区编码兼容：西湖区 / A001 等 —— CSV 用中文区名
        district = area_id
        if area_id in ('A001', 'all', ''):
            district = 'all' if area_id in ('all', '') else '西湖区'
        elif area_id == 'B001':
            district = '拱墅区'
        elif area_id == 'C001':
            district = '滨江区'
        elif area_id == 'D001':
            district = '余杭区'

        rows = self.meter_store.by_district(district, limit=meter_count)
        if not rows and district != 'all':
            rows = self.meter_store.sample(meter_count)

        logger.info(f"抄表智能体: CSV批量抄表, 区域: {district}, 水表数: {len(rows)}")

        readings = []
        for r in rows:
            readings.append({
                'meter_id': r['meter_id'],
                'reading': r['reading'],
                'confidence': r['confidence'],
                'mode': 'schedule',
                'water_usage': r['water_usage'],
                'alarm': r['alarm'],
                'district': r['district'],
                'community': r['community'],
            })

        perceived_data = {
            'area_id': district,
            'mode': 'schedule',
            'total_meters': len(readings),
            'readings': readings,
            'timestamp': datetime.now().isoformat()
        }
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95 if readings else 0.0,
            source='csv_schedule',
            metadata={'batch_size': len(readings), 'csv': True}
        )

    # ==================== 决策 ====================

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """
        决策: 判断读数有效性，决定后续动作
        
        决策树:
        1. 置信度 >= 0.95 → 自动接受，录入读数
        2. 0.80 <= 置信度 < 0.95 → 人工复核
        3. 置信度 < 0.80 → 拒绝，重新抄表
        4. 读数合理性校验（与历史对比）
        """
        data = perception.data or {}
        mode = data.get('mode', 'remote')
        
        if mode == 'schedule':
            return self._decide_schedule(data)
        
        reading = data.get('reading', 0)
        confidence = perception.confidence
        meter_id = data.get('meter_id', 'unknown')
        last_reading = data.get('last_reading', 0)
        
        # 读数合理性校验
        validation = self._validate_reading(reading, last_reading)
        
        reasoning_parts = []
        action = "auto_accept"
        params = {'meter_id': meter_id, 'reading': reading}
        
        # 置信度判断
        if data.get('alarm'):
            action = "human_review"
            reasoning_parts.append(f"CSV告警位=1（状态:{data.get('status')}），转人工复核")
            params['anomaly_type'] = 'sensor_alarm'
            params['notify_anomaly'] = True
        elif confidence >= self.confidence_threshold and validation['valid']:
            action = "auto_accept"
            reasoning_parts.append(f"置信度{confidence:.2f}≥{self.confidence_threshold}，读数合理")
        elif confidence >= self.review_threshold and validation['valid']:
            action = "human_review"
            reasoning_parts.append(f"置信度{confidence:.2f}在复核区间，需人工确认")
        elif not validation['valid']:
            action = "reject_invalid"
            reasoning_parts.append(f"读数不合理: {validation['reason']}")
        else:
            action = "reject_low_confidence"
            reasoning_parts.append(f"置信度{confidence:.2f}<{self.review_threshold}，需重新抄表")
        
        # 如果读数异常，同时通知异常智能体
        if validation.get('anomaly'):
            params['notify_anomaly'] = True
            params['anomaly_type'] = validation.get('anomaly_type', 'unknown')
            reasoning_parts.append(f"检测到异常: {validation.get('anomaly_type')}")
        
        return DecisionResult(
            action=action,
            params=params,
            confidence=confidence,
            reasoning="; ".join(reasoning_parts),
            alternatives=[
                {'action': 'human_review', 'description': '转人工复核'},
                {'action': 're_read', 'description': '重新抄表'}
            ] if action == 'auto_accept' else []
        )

    def _decide_schedule(self, data: Dict) -> DecisionResult:
        """批量抄表决策"""
        readings = data.get('readings', [])
        auto_count = 0
        review_count = 0
        reject_count = 0
        
        for r in readings:
            if r.get('alarm'):
                review_count += 1
            elif r['confidence'] >= self.confidence_threshold:
                auto_count += 1
            elif r['confidence'] >= self.review_threshold:
                review_count += 1
            else:
                reject_count += 1
        
        return DecisionResult(
            action="batch_process",
            params={
                'area_id': data.get('area_id'),
                'auto_accept_count': auto_count,
                'human_review_count': review_count,
                'reject_count': reject_count,
                'readings': readings
            },
            confidence=0.95,
            reasoning=f"批量CSV抄表: 自动接受{auto_count}个, 人工复核{review_count}个, 拒绝{reject_count}个"
        )

    # ==================== 执行 ====================

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """执行决策"""
        action = decision.action
        params = decision.params
        
        if action == "auto_accept":
            return self._execute_auto_accept(params)
        elif action == "human_review":
            return self._execute_human_review(params)
        elif action == "reject_invalid":
            return self._execute_reject(params, "读数不合理")
        elif action == "reject_low_confidence":
            return self._execute_reject(params, "置信度过低")
        elif action == "batch_process":
            return self._execute_batch_process(params)
        else:
            return ExecutionResult(success=False, message=f"未知动作: {action}")

    def _execute_auto_accept(self, params: Dict) -> ExecutionResult:
        """自动接受读数"""
        meter_id = params.get('meter_id', 'unknown')
        reading = params.get('reading', 0)
        
        # 记录抄表结果
        record = {
            'meter_id': meter_id,
            'reading': reading,
            'status': 'accepted',
            'method': 'ai_auto',
            'timestamp': datetime.now().isoformat()
        }
        self.completed_readings.append(record)
        
        # 更新知识库
        self.knowledge_base.set_fact(f"meter_last_reading:{meter_id}", reading, self.agent_id)
        
        logger.info(f"抄表智能体: 自动录入水表 [{meter_id}] 读数: {reading}")
        
        return ExecutionResult(
            success=True,
            data=record,
            message=f"水表{meter_id}读数{reading}已自动录入",
            next_actions=['trigger_billing']  # 触发计费
        )

    def _execute_human_review(self, params: Dict) -> ExecutionResult:
        """转人工复核"""
        meter_id = params.get('meter_id', 'unknown')
        reading = params.get('reading', 0)
        
        reason = '传感器告警，需人工确认' if params.get('anomaly_type') == 'sensor_alarm' else '置信度不足，需人工确认'
        review_record = {
            'meter_id': meter_id,
            'reading': reading,
            'status': 'pending_review',
            'method': 'ai_suggest',
            'timestamp': datetime.now().isoformat(),
            'review_reason': reason
        }
        self.pending_reviews.append(review_record)
        
        logger.info(f"抄表智能体: 水表 [{meter_id}] 读数转人工复核")
        
        return ExecutionResult(
            success=True,
            data=review_record,
            message=f"水表{meter_id}读数已转人工复核队列",
            next_actions=['wait_for_review']
        )

    def _execute_reject(self, params: Dict, reason: str) -> ExecutionResult:
        """拒绝读数"""
        meter_id = params.get('meter_id', 'unknown')
        
        # 如果需要通知异常智能体
        if params.get('notify_anomaly'):
            self._notify_anomaly_agent(meter_id, params.get('anomaly_type', 'reading_anomaly'))
        
        logger.warning(f"抄表智能体: 水表 [{meter_id}] 读数被拒绝, 原因: {reason}")
        
        return ExecutionResult(
            success=False,
            message=f"水表{meter_id}读数被拒绝: {reason}",
            next_actions=['re_read', 'manual_read']
        )

    def _execute_batch_process(self, params: Dict) -> ExecutionResult:
        """批量处理抄表结果"""
        readings = params.get('readings', [])
        accepted = []
        reviewed = []
        
        for r in readings:
            if (not r.get('alarm')) and r['confidence'] >= self.confidence_threshold:
                record = {
                    'meter_id': r['meter_id'],
                    'reading': r['reading'],
                    'status': 'accepted',
                    'method': 'ai_batch_csv',
                    'timestamp': datetime.now().isoformat()
                }
                self.completed_readings.append(record)
                accepted.append(record)
            else:
                self.pending_reviews.append({
                    'meter_id': r['meter_id'],
                    'reading': r['reading'],
                    'status': 'pending_review',
                    'timestamp': datetime.now().isoformat(),
                    'review_reason': '传感器告警' if r.get('alarm') else '置信度不足'
                })
                reviewed.append(r['meter_id'])
        
        result_data = {
            'accepted_count': len(accepted),
            'review_count': len(reviewed),
            'accepted': accepted,
            'review_ids': reviewed
        }
        
        logger.info(f"抄表智能体: 批量处理完成, 接受: {len(accepted)}, 复核: {len(reviewed)}")
        
        return ExecutionResult(
            success=True,
            data=result_data,
            message=f"批量抄表处理完成: 接受{len(accepted)}个, 复核{len(reviewed)}个",
            next_actions=['trigger_batch_billing']
        )

    # ==================== 辅助方法 ====================

    def _validate_reading(self, current: float, last: float) -> Dict:
        """
        读数合理性校验
        """
        # 读数应单调递增
        if current < last:
            return {
                'valid': False,
                'reason': f'读数倒退: 当前{current} < 上次{last}',
                'anomaly': True,
                'anomaly_type': 'reading_regression'
            }
        
        # 用量变化率检查
        if last > 0:
            change_rate = (current - last) / last
            max_change = self.knowledge_base.get_rule('meter_reading', 'reading_validation')
            max_rate = max_change.get('max_change_rate', 50) if max_change else 50
            
            if (current - last) > max_rate:
                return {
                    'valid': True,  # 仍然有效，但标记异常
                    'reason': f'用量变化较大: {current - last:.1f}吨',
                    'anomaly': True,
                    'anomaly_type': 'sudden_increase'
                }
        
        return {'valid': True, 'reason': '读数合理', 'anomaly': False}

    def _get_last_reading(self, meter_id: str) -> float:
        """获取上次读数"""
        # 从知识库获取
        last = self.knowledge_base.get_fact(f"meter_last_reading:{meter_id}")
        if last is not None:
            return float(last)
        # 从已完成记录中查找
        for record in reversed(self.completed_readings):
            if record['meter_id'] == meter_id:
                return float(record['reading'])
        return 0.0

    def _notify_anomaly_agent(self, meter_id: str, anomaly_type: str):
        """通知异常智能体"""
        msg = AgentMessage(
            sender=self.agent_id,
            receiver='anomaly_agent',
            msg_type='event',
            subject='reading_anomaly_detected',
            content={
                'meter_id': meter_id,
                'anomaly_type': anomaly_type,
                'source': 'meter_reading_agent',
                'timestamp': datetime.now().isoformat()
            },
            priority=2  # 高优先级
        )
        self.send_message(msg)

    def _on_event(self, msg: AgentMessage):
        """处理事件"""
        if msg.subject == 'reading_anomaly_detected':
            logger.info(f"抄表智能体: 收到异常事件, 水表: {msg.content.get('meter_id')}")
        elif msg.subject == 'schedule_reading_task':
            # 收到定时抄表任务
            self.reading_tasks.append(msg.content)

    def get_reading_stats(self) -> Dict:
        """获取抄表统计"""
        total = len(self.completed_readings) + len(self.pending_reviews)
        return {
            'total_readings': total,
            'auto_accepted': len(self.completed_readings),
            'pending_reviews': len(self.pending_reviews),
            'auto_rate': len(self.completed_readings) / max(total, 1),
            'data_source': 'csv',
            'csv_meters': self.meter_store.stats()['total'],
            'ocr_model_loaded': self.ocr_model.loaded
        }
