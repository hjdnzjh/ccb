"""
催缴智能体 (Collection Agent)
核心能力: 智能催缴策略 + 用户画像 + 差异化催缴 + 效果跟踪
自主感知: 感知欠费状态、用户行为、催缴效果
自主决策: 选择最优催缴策略、判断催缴时机
自主执行: 执行催缴动作（短信/电话/停水）、跟踪结果
"""
import time
import random
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
from loguru import logger

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.base_agent import (
    BaseAgent, AgentState, AgentCapability,
    PerceptionResult, DecisionResult, ExecutionResult, AgentMessage
)
from core.knowledge_base import KnowledgeBase


class UserProfiler:
    """用户画像分析器"""
    
    def __init__(self):
        self.profiles = {}
    
    def analyze(self, user_data: Dict) -> Dict:
        """
        分析用户画像
        
        维度:
        1. 支付习惯: 准时/偶尔逾期/经常逾期/恶意拖欠
        2. 支付能力: 高/中/低/困难
        3. 联系偏好: 短信/电话/APP推送
        4. 信用等级: A/B/C/D
        """
        user_id = user_data.get('user_id', 'unknown')
        payment_history = user_data.get('payment_history', [])
        overdue_history = user_data.get('overdue_history', [])
        
        # 支付习惯分析
        if len(payment_history) >= 3:
            on_time_rate = sum(1 for p in payment_history[-10:] if p.get('on_time', True)) / len(payment_history[-10:])
            if on_time_rate >= 0.9:
                payment_habit = 'punctual'
            elif on_time_rate >= 0.7:
                payment_habit = 'occasional_delay'
            elif on_time_rate >= 0.5:
                payment_habit = 'frequent_delay'
            else:
                payment_habit = 'habitual_defaulter'
        else:
            payment_habit = 'unknown'
        
        # 信用等级
        if payment_habit == 'punctual':
            credit_level = 'A'
        elif payment_habit == 'occasional_delay':
            credit_level = 'B'
        elif payment_habit == 'frequent_delay':
            credit_level = 'C'
        else:
            credit_level = 'D'
        
        # 支付能力估计
        avg_amount = sum(p.get('amount', 0) for p in payment_history[-6:]) / max(len(payment_history[-6:]), 1)
        if avg_amount > 200:
            payment_capacity = 'high'
        elif avg_amount > 100:
            payment_capacity = 'medium'
        else:
            payment_capacity = 'low'
        
        # 联系偏好（简化）
        contact_preference = random.choice(['sms', 'phone', 'app', 'wechat'])
        
        return {
            'user_id': user_id,
            'payment_habit': payment_habit,
            'credit_level': credit_level,
            'payment_capacity': payment_capacity,
            'contact_preference': contact_preference,
            'on_time_rate': on_time_rate if 'on_time_rate' in dir() else 0.8,
            'analysis_time': datetime.now().isoformat()
        }


class CollectionStrategy:
    """催缴策略引擎"""
    
    def __init__(self):
        self.strategy_templates = {
            'gentle': {
                'name': '温和提醒',
                'channels': ['sms', 'app'],
                'template': 'friendly_reminder',
                'frequency': 'once',
                'suitable_for': ['punctual', 'occasional_delay']
            },
            'formal': {
                'name': '正式通知',
                'channels': ['sms', 'letter'],
                'template': 'formal_notice',
                'frequency': 'twice',
                'suitable_for': ['occasional_delay', 'frequent_delay']
            },
            'intensive': {
                'name': '强化催缴',
                'channels': ['sms', 'phone', 'app', 'letter'],
                'template': 'urgent_notice',
                'frequency': 'repeated',
                'suitable_for': ['frequent_delay', 'habitual_defaulter']
            },
            'restrictive': {
                'name': '限制措施',
                'channels': ['sms', 'letter', 'door_to_door'],
                'template': 'restriction_warning',
                'actions': ['water_restriction', 'legal_action'],
                'suitable_for': ['habitual_defaulter']
            }
        }
    
    def select_strategy(self, user_profile: Dict, overdue_days: int, overdue_amount: float) -> Dict:
        """选择催缴策略"""
        habit = user_profile.get('payment_habit', 'unknown')
        credit = user_profile.get('credit_level', 'C')
        preference = user_profile.get('contact_preference', 'sms')
        
        # 根据逾期天数和用户画像选择
        if overdue_days <= 7:
            strategy_key = 'gentle'
        elif overdue_days <= 30:
            strategy_key = 'formal' if credit in ['A', 'B'] else 'intensive'
        elif overdue_days <= 60:
            strategy_key = 'intensive' if credit in ['A', 'B', 'C'] else 'restrictive'
        else:
            strategy_key = 'restrictive'
        
        strategy = self.strategy_templates[strategy_key].copy()
        strategy['strategy_key'] = strategy_key
        strategy['selected_channel'] = preference
        strategy['reasoning'] = f"逾期{overdue_days}天, 信用等级{credit}, 支付习惯{habit}"
        
        return strategy


class CollectionAgent(BaseAgent):
    """
    催缴智能体
    
    感知能力:
    - 感知账单欠费状态
    - 感知用户支付行为
    - 感知催缴效果反馈
    
    决策能力:
    - 用户画像分析
    - 催缴策略选择
    - 催缴时机判断
    
    执行能力:
    - 发送催缴通知
    - 执行限制措施
    - 跟踪催缴结果
    """

    def __init__(self, knowledge_base: KnowledgeBase = None):
        super().__init__(
            agent_id="collection_agent",
            agent_name="催缴智能体",
            description="负责智能催缴、用户画像分析和催缴策略执行"
        )
        self.capabilities = [
            AgentCapability.PERCEIVE,
            AgentCapability.DECIDE,
            AgentCapability.EXECUTE,
            AgentCapability.LEARN
        ]
        
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.profiler = UserProfiler()
        self.strategy_engine = CollectionStrategy()
        
        # 催缴记录
        self.collection_records: List[Dict] = []
        self.active_collections: Dict[str, Dict] = {}

    def _on_start(self):
        logger.info("催缴智能体: 初始化完成，策略引擎已就绪")

    # ==================== 感知 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """
        感知欠费数据
        
        模式:
        1. overdue_check: 检查欠费账单
        2. single_user: 单用户催缴
        3. batch: 批量催缴扫描
        """
        mode = context.get('mode', 'overdue_check')
        
        if mode == 'overdue_check':
            return self._perceive_overdue_check(context)
        elif mode == 'single_user':
            return self._perceive_single_user(context)
        elif mode == 'batch':
            return self._perceive_batch(context)
        else:
            return PerceptionResult(confidence=0.0, metadata={'error': f'未知模式: {mode}'})

    def _perceive_overdue_check(self, context: Dict) -> PerceptionResult:
        """感知欠费检查"""
        # 模拟获取欠费账单
        overdue_bills = context.get('overdue_bills', [])
        if not overdue_bills:
            overdue_bills = self._simulate_overdue_bills()
        
        perceived_data = {
            'mode': 'overdue_check',
            'overdue_bills': overdue_bills,
            'total_overdue_amount': sum(b.get('amount', 0) for b in overdue_bills),
            'overdue_count': len(overdue_bills),
            'check_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95,
            source='billing_system',
            metadata={'scan_range': context.get('scan_range', 'all')}
        )

    def _perceive_single_user(self, context: Dict) -> PerceptionResult:
        """感知单用户"""
        user_id = context.get('user_id', 'unknown')
        bill_id = context.get('bill_id', '')
        overdue_amount = context.get('overdue_amount', 0)
        overdue_days = context.get('overdue_days', 0)
        
        # 用户历史数据
        user_history = context.get('user_history', {
            'payment_history': [{'amount': random.randint(50, 200), 'on_time': random.random() > 0.3} for _ in range(6)],
            'overdue_history': []
        })
        
        perceived_data = {
            'mode': 'single_user',
            'user_id': user_id,
            'bill_id': bill_id,
            'overdue_amount': overdue_amount,
            'overdue_days': overdue_days,
            'user_history': user_history,
            'perceive_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95,
            source='user_check',
            metadata={'priority': 3}
        )

    def _perceive_batch(self, context: Dict) -> PerceptionResult:
        """批量感知"""
        users = context.get('users', [])
        if not users:
            users = [{'user_id': f'U{i:04d}', 'overdue_days': random.randint(1, 90), 
                      'overdue_amount': random.randint(50, 500)} for i in range(1, 11)]
        
        perceived_data = {
            'mode': 'batch',
            'users': users,
            'total_users': len(users),
            'batch_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.90,
            source='batch_scan',
            metadata={'batch_id': f"BATCH-{int(time.time())}"}
        )

    # ==================== 决策 ====================

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """催缴决策"""
        data = perception.data or {}
        mode = data.get('mode', 'overdue_check')
        
        if mode == 'batch':
            return self._decide_batch(data)
        
        # 单用户决策
        user_id = data.get('user_id', 'unknown')
        overdue_days = data.get('overdue_days', 0)
        overdue_amount = data.get('overdue_amount', 0)
        user_history = data.get('user_history', {})
        
        # 用户画像分析
        user_profile = self.profiler.analyze({**user_history, 'user_id': user_id})
        
        # 策略选择
        strategy = self.strategy_engine.select_strategy(user_profile, overdue_days, overdue_amount)
        
        # 决策内容
        action = "execute_collection"
        params = {
            'user_id': user_id,
            'bill_id': data.get('bill_id'),
            'overdue_amount': overdue_amount,
            'overdue_days': overdue_days,
            'user_profile': user_profile,
            'strategy': strategy,
            'channels': [strategy.get('selected_channel', 'sms')],
            'template': strategy.get('template', 'friendly_reminder')
        }
        
        reasoning = f"用户{user_id}: 逾期{overdue_days}天, {overdue_amount}元; "
        reasoning += f"画像: {user_profile.get('payment_habit')}, 信用{user_profile.get('credit_level')}; "
        reasoning += f"策略: {strategy.get('name')}"
        
        return DecisionResult(
            action=action,
            params=params,
            confidence=0.90,
            reasoning=reasoning,
            alternatives=[
                {'action': 'send_reminder', 'description': '发送提醒'},
                {'action': 'phone_call', 'description': '电话催缴'},
                {'action': 'restrict_water', 'description': '限制供水'}
            ] if overdue_days > 30 else []
        )

    def _decide_batch(self, data: Dict) -> DecisionResult:
        """批量决策"""
        users = data.get('users', [])
        decisions = []
        
        for user in users[:10]:  # 演示限制
            user_profile = self.profiler.analyze({
                'user_id': user.get('user_id'),
                'payment_history': [{'amount': 100, 'on_time': True} for _ in range(6)]
            })
            strategy = self.strategy_engine.select_strategy(
                user_profile, 
                user.get('overdue_days', 0),
                user.get('overdue_amount', 0)
            )
            decisions.append({
                'user_id': user.get('user_id'),
                'strategy': strategy.get('strategy_key'),
                'channel': strategy.get('selected_channel')
            })
        
        return DecisionResult(
            action="batch_execute",
            params={
                'decisions': decisions,
                'total': len(decisions)
            },
            confidence=0.85,
            reasoning=f"批量催缴决策: {len(decisions)}户"
        )

    # ==================== 执行 ====================

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """执行催缴"""
        action = decision.action
        params = decision.params
        
        if action == "execute_collection":
            return self._execute_collection(params)
        elif action == "batch_execute":
            return self._execute_batch(params)
        else:
            return ExecutionResult(success=False, message=f"未知动作: {action}")

    def _execute_collection(self, params: Dict) -> ExecutionResult:
        """执行单户催缴"""
        user_id = params.get('user_id')
        strategy = params.get('strategy', {})
        channels = params.get('channels', ['sms'])
        template = params.get('template', 'friendly_reminder')
        
        # 创建催缴记录
        collection_id = f"COLL-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        record = {
            'collection_id': collection_id,
            'user_id': user_id,
            'overdue_amount': params.get('overdue_amount'),
            'overdue_days': params.get('overdue_days'),
            'strategy': strategy.get('strategy_key', 'gentle'),
            'channels': channels,
            'template': template,
            'status': 'sent',
            'sent_at': datetime.now().isoformat(),
            'result': None
        }
        
        self.collection_records.append(record)
        self.active_collections[collection_id] = record
        
        logger.info(f"催缴智能体: 执行催缴 [{collection_id}] → 用户{user_id}, 策略: {strategy.get('name')}")
        
        return ExecutionResult(
            success=True,
            data=record,
            message=f"催缴通知已发送: 用户{user_id}, 渠道: {channels}",
            next_actions=['track_result', 'schedule_followup']
        )

    def _execute_batch(self, params: Dict) -> ExecutionResult:
        """执行批量催缴"""
        decisions = params.get('decisions', [])
        sent_count = 0
        
        for d in decisions:
            collection_id = f"COLL-{datetime.now().strftime('%Y%m%d%H%M%S')}-{sent_count}"
            record = {
                'collection_id': collection_id,
                'user_id': d.get('user_id'),
                'strategy': d.get('strategy'),
                'channel': d.get('channel'),
                'status': 'sent',
                'sent_at': datetime.now().isoformat()
            }
            self.collection_records.append(record)
            sent_count += 1
        
        logger.info(f"催缴智能体: 批量催缴完成, 发送{sent_count}条")
        
        return ExecutionResult(
            success=True,
            data={'sent_count': sent_count},
            message=f"批量催缴完成: {sent_count}户"
        )

    # ==================== 辅助方法 ====================

    def _simulate_overdue_bills(self) -> List[Dict]:
        """模拟欠费账单"""
        return [
            {'bill_id': f'B{i:04d}', 'user_id': f'U{i:03d}', 
             'amount': random.randint(50, 300), 'overdue_days': random.randint(1, 90)}
            for i in range(1, 6)
        ]

    def get_collection_stats(self) -> Dict:
        """获取催缴统计"""
        total = len(self.collection_records)
        success = sum(1 for r in self.collection_records if r.get('result') == 'paid')
        
        return {
            'total_collections': total,
            'active_collections': len(self.active_collections),
            'success_rate': success / max(total, 1),
            'by_strategy': {
                'gentle': sum(1 for r in self.collection_records if r.get('strategy') == 'gentle'),
                'formal': sum(1 for r in self.collection_records if r.get('strategy') == 'formal'),
                'intensive': sum(1 for r in self.collection_records if r.get('strategy') == 'intensive'),
                'restrictive': sum(1 for r in self.collection_records if r.get('strategy') == 'restrictive')
            }
        }