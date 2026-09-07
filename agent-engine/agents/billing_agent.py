"""
计费智能体 (Billing Agent)
核心能力: 自动计费 + 阶梯水价 + 账单生成 + 优惠计算
自主感知: 感知抄表数据、费率变化、用户类型
自主决策: 选择最优费率方案、判断优惠适用
自主执行: 生成账单、发送通知、触发催缴
"""
import time
import random
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


class BillingAgent(BaseAgent):
    """
    计费智能体
    
    感知能力:
    - 感知抄表智能体提交的读数数据
    - 感知费率规则变化
    - 感知用户类型和优惠信息
    
    决策能力:
    - 根据用户类型选择费率方案
    - 计算阶梯水价
    - 判断优惠适用条件
    
    执行能力:
    - 生成账单
    - 计算应缴金额
    - 触发通知和催缴
    """

    def __init__(self, knowledge_base: KnowledgeBase = None):
        super().__init__(
            agent_id="billing_agent",
            agent_name="计费智能体",
            description="负责自动计费、阶梯水价计算、账单生成和优惠管理"
        )
        self.capabilities = [
            AgentCapability.PERCEIVE,
            AgentCapability.DECIDE,
            AgentCapability.EXECUTE,
            AgentCapability.LEARN
        ]
        
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.bills: List[Dict] = []
        self.pending_bills: List[Dict] = []

    def _on_start(self):
        logger.info("计费智能体: 初始化完成，费率规则已加载")

    # ==================== 感知 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """
        感知计费相关数据
        
        模式:
        1. single: 单户计费 - 一个水表的抄表数据
        2. batch: 批量计费 - 多个水表的抄表数据
        3. rate_change: 费率变更 - 感知费率调整
        """
        mode = context.get('mode', 'single')
        
        if mode == 'single':
            return self._perceive_single(context)
        elif mode == 'batch':
            return self._perceive_batch(context)
        elif mode == 'rate_change':
            return self._perceive_rate_change(context)
        else:
            return PerceptionResult(confidence=0.0, metadata={'error': f'未知模式: {mode}'})

    def _perceive_single(self, context: Dict) -> PerceptionResult:
        """单户计费感知"""
        meter_id = context.get('meter_id', 'unknown')
        current_reading = context.get('reading', 0)
        last_reading = context.get('last_reading', 0)
        user_type = context.get('user_type', 'residential')
        
        usage = current_reading - last_reading
        
        # 获取费率规则
        pricing_rules = self.knowledge_base.get_rule('pricing', f'{user_type}_ladder' if user_type == 'residential' else user_type)
        
        perceived_data = {
            'meter_id': meter_id,
            'current_reading': current_reading,
            'last_reading': last_reading,
            'usage': usage,
            'user_type': user_type,
            'pricing_rules': pricing_rules,
            'billing_period': context.get('billing_period', datetime.now().strftime('%Y-%m')),
            'timestamp': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.99,
            source='meter_reading',
            metadata={'usage': usage}
        )

    def _perceive_batch(self, context: Dict) -> PerceptionResult:
        """批量计费感知"""
        readings = context.get('readings', [])
        period = context.get('billing_period', datetime.now().strftime('%Y-%m'))
        
        perceived_data = {
            'mode': 'batch',
            'readings': readings,
            'billing_period': period,
            'total_meters': len(readings),
            'timestamp': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.99,
            source='batch_reading',
            metadata={'count': len(readings)}
        )

    def _perceive_rate_change(self, context: Dict) -> PerceptionResult:
        """费率变更感知"""
        new_rates = context.get('new_rates', {})
        
        return PerceptionResult(
            data={'new_rates': new_rates, 'effective_date': context.get('effective_date')},
            confidence=1.0,
            source='admin',
            metadata={'change_type': 'rate_update'}
        )

    # ==================== 决策 ====================

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """
        计费决策
        
        决策逻辑:
        1. 确定用户类型 → 选择费率方案
        2. 计算阶梯水价（居民）或固定水价（商业/工业）
        3. 判断优惠条件
        4. 计算最终金额
        """
        data = perception.data or {}
        mode = data.get('mode', 'single')
        
        if mode == 'batch':
            return self._decide_batch(data)
        
        usage = data.get('usage', 0)
        user_type = data.get('user_type', 'residential')
        pricing_rules = data.get('pricing_rules')
        
        # 计算费用
        if user_type == 'residential' and pricing_rules:
            amount, details = self._calculate_ladder_price(usage, pricing_rules)
            reasoning = f"居民阶梯水价: 用量{usage}吨, 金额{amount}元"
        elif user_type == 'commercial':
            price = pricing_rules.get('price', 4.50) if pricing_rules else 4.50
            amount = round(usage * price, 2)
            details = [{'range': '全部', 'usage': usage, 'price': price, 'amount': amount}]
            reasoning = f"商业用水: {usage}吨 × {price}元/吨 = {amount}元"
        elif user_type == 'industrial':
            price = pricing_rules.get('price', 5.80) if pricing_rules else 5.80
            amount = round(usage * price, 2)
            details = [{'range': '全部', 'usage': usage, 'price': price, 'amount': amount}]
            reasoning = f"工业用水: {usage}吨 × {price}元/吨 = {amount}元"
        else:
            amount = round(usage * 2.07, 2)
            details = [{'range': '默认', 'usage': usage, 'price': 2.07, 'amount': amount}]
            reasoning = f"默认费率: {usage}吨 × 2.07元/吨 = {amount}元"
        
        # 污水处理费（通常为水费的1.1倍左右）
        sewage_fee = round(amount * 0.9, 2)
        total_amount = round(amount + sewage_fee, 2)
        
        reasoning += f", 污水处理费{sewage_fee}元, 合计{total_amount}元"
        
        return DecisionResult(
            action="generate_bill",
            params={
                'meter_id': data.get('meter_id'),
                'usage': usage,
                'water_fee': amount,
                'sewage_fee': sewage_fee,
                'total_amount': total_amount,
                'details': details,
                'user_type': user_type,
                'billing_period': data.get('billing_period'),
                'current_reading': data.get('current_reading'),
                'last_reading': data.get('last_reading')
            },
            confidence=0.99,
            reasoning=reasoning
        )

    def _decide_batch(self, data: Dict) -> DecisionResult:
        """批量计费决策"""
        readings = data.get('readings', [])
        bill_params_list = []
        total_amount = 0
        
        for r in readings:
            usage = r.get('usage', r.get('reading', 0))
            user_type = r.get('user_type', 'residential')
            pricing_rules = self.knowledge_base.get_rule('pricing', 
                f'{user_type}_ladder' if user_type == 'residential' else user_type)
            
            if user_type == 'residential' and pricing_rules:
                amount, details = self._calculate_ladder_price(usage, pricing_rules)
            else:
                price = (pricing_rules or {}).get('price', 4.50)
                amount = round(usage * price, 2)
                details = [{'usage': usage, 'price': price, 'amount': amount}]
            
            sewage_fee = round(amount * 0.9, 2)
            total = round(amount + sewage_fee, 2)
            total_amount += total
            
            bill_params_list.append({
                'meter_id': r.get('meter_id'),
                'usage': usage,
                'water_fee': amount,
                'sewage_fee': sewage_fee,
                'total_amount': total,
                'details': details,
                'user_type': user_type,
                'billing_period': data.get('billing_period')
            })
        
        return DecisionResult(
            action="batch_generate_bills",
            params={
                'bills': bill_params_list,
                'total_amount': round(total_amount, 2),
                'bill_count': len(bill_params_list),
                'billing_period': data.get('billing_period')
            },
            confidence=0.99,
            reasoning=f"批量计费完成: {len(bill_params_list)}户, 总金额{total_amount:.2f}元"
        )

    # ==================== 执行 ====================

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """执行计费"""
        action = decision.action
        
        if action == "generate_bill":
            return self._execute_generate_bill(decision.params)
        elif action == "batch_generate_bills":
            return self._execute_batch_generate(decision.params)
        else:
            return ExecutionResult(success=False, message=f"未知动作: {action}")

    def _execute_generate_bill(self, params: Dict) -> ExecutionResult:
        """生成单户账单"""
        bill_id = f"BILL-{datetime.now().strftime('%Y%m%d')}-{random.randint(1000, 9999)}"
        
        bill = {
            'bill_id': bill_id,
            'meter_id': params.get('meter_id'),
            'billing_period': params.get('billing_period', datetime.now().strftime('%Y-%m')),
            'current_reading': params.get('current_reading', 0),
            'last_reading': params.get('last_reading', 0),
            'usage': params.get('usage', 0),
            'water_fee': params.get('water_fee', 0),
            'sewage_fee': params.get('sewage_fee', 0),
            'total_amount': params.get('total_amount', 0),
            'details': params.get('details', []),
            'user_type': params.get('user_type', 'residential'),
            'status': 'unpaid',
            'due_date': (datetime.now() + timedelta(days=15)).strftime('%Y-%m-%d'),
            'created_at': datetime.now().isoformat(),
            'created_by': self.agent_id
        }
        
        self.bills.append(bill)
        
        # 更新知识库
        self.knowledge_base.set_fact(f"bill:{bill_id}", bill, self.agent_id)
        
        logger.info(f"计费智能体: 生成账单 [{bill_id}], 金额: {bill['total_amount']}元")
        
        return ExecutionResult(
            success=True,
            data=bill,
            message=f"账单{bill_id}已生成，应缴{bill['total_amount']}元",
            next_actions=['notify_user', 'trigger_collection_check']
        )

    def _execute_batch_generate(self, params: Dict) -> ExecutionResult:
        """批量生成账单"""
        bills = params.get('bills', [])
        generated = []
        
        for bill_params in bills:
            bill_id = f"BILL-{datetime.now().strftime('%Y%m%d')}-{random.randint(1000, 9999)}"
            bill = {
                'bill_id': bill_id,
                **bill_params,
                'status': 'unpaid',
                'due_date': (datetime.now() + timedelta(days=15)).strftime('%Y-%m-%d'),
                'created_at': datetime.now().isoformat(),
                'created_by': self.agent_id
            }
            self.bills.append(bill)
            generated.append(bill)
            self.knowledge_base.set_fact(f"bill:{bill_id}", bill, self.agent_id)
        
        logger.info(f"计费智能体: 批量生成{len(generated)}张账单, 总金额: {params.get('total_amount', 0)}元")
        
        return ExecutionResult(
            success=True,
            data={'generated_count': len(generated), 'total_amount': params.get('total_amount', 0), 'bills': generated},
            message=f"批量生成{len(generated)}张账单",
            next_actions=['batch_notify', 'trigger_collection_check']
        )

    # ==================== 阶梯水价计算 ====================

    def _calculate_ladder_price(self, usage: float, pricing_rules: Dict) -> tuple:
        """
        计算阶梯水价
        
        居民阶梯水价示例:
        第一阶梯: 0-180吨, 2.07元/吨
        第二阶梯: 181-260吨, 3.10元/吨
        第三阶梯: 261吨以上, 4.65元/吨
        """
        ladders = pricing_rules.get('ladders', [])
        if not ladders:
            return round(usage * 2.07, 2), [{'range': '默认', 'usage': usage, 'price': 2.07, 'amount': round(usage * 2.07, 2)}]
        
        total_amount = 0
        details = []
        remaining = usage
        
        for ladder in ladders:
            min_val = ladder.get('min', 0)
            max_val = ladder.get('max')
            price = ladder.get('price', 2.07)
            
            if remaining <= 0:
                break
            
            ladder_range = max_val - min_val if max_val else remaining
            used_in_ladder = min(remaining, ladder_range)
            amount = round(used_in_ladder * price, 2)
            
            total_amount += amount
            details.append({
                'range': f"{min_val}-{max_val or '∞'}吨",
                'usage': used_in_ladder,
                'price': price,
                'amount': amount
            })
            remaining -= used_in_ladder
        
        return round(total_amount, 2), details

    def _on_event(self, msg: AgentMessage):
        """处理事件"""
        if msg.subject == 'reading_accepted':
            # 抄表智能体通知读数已接受，触发计费
            logger.info(f"计费智能体: 收到抄表完成事件，准备计费")
        elif msg.subject == 'rate_change':
            # 费率变更
            logger.info(f"计费智能体: 收到费率变更通知")

    def get_billing_stats(self) -> Dict:
        """获取计费统计"""
        total_bills = len(self.bills)
        unpaid = [b for b in self.bills if b['status'] == 'unpaid']
        total_amount = sum(b.get('total_amount', 0) for b in self.bills)
        unpaid_amount = sum(b.get('total_amount', 0) for b in unpaid)
        
        return {
            'total_bills': total_bills,
            'unpaid_bills': len(unpaid),
            'total_amount': round(total_amount, 2),
            'unpaid_amount': round(unpaid_amount, 2),
            'collection_rate': round((total_amount - unpaid_amount) / max(total_amount, 1), 4)
        }
