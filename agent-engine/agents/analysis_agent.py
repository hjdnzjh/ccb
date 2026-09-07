"""
分析智能体 (Analysis Agent)
核心能力: 用水量分析 + 趋势预测 + 异常洞察 + 报告生成
自主感知: 感知历史用水数据、区域用水模式、外部因素
自主决策: 选择分析模型、识别趋势特征、生成优化建议
自主执行: 生成分析报告、推送洞察、触发预警
"""
import time
import random
import numpy as np
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta
from collections import defaultdict
from loguru import logger

import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.base_agent import (
    BaseAgent, AgentState, AgentCapability,
    PerceptionResult, DecisionResult, ExecutionResult, AgentMessage
)
from core.knowledge_base import KnowledgeBase


class TimeSeriesForecaster:
    """时序预测模型"""
    
    def __init__(self):
        self.model_loaded = False
        self._load_model()
    
    def _load_model(self):
        """加载预测模型"""
        # 实际项目中: 加载Prophet/LSTM模型
        self.model_loaded = True
        logger.info("分析智能体: 时序预测模型加载完成")
    
    def forecast(self, history: List[float], periods: int = 30) -> Dict:
        """
        预测未来用水量
        
        Args:
            history: 历史用水数据
            periods: 预测天数
        
        Returns:
            {'forecast': list, 'confidence_lower': list, 'confidence_upper': list, 'trend': str}
        """
        if len(history) < 7:
            return {'error': '历史数据不足'}
        
        # 简化预测：线性趋势 + 季节性
        values = np.array(history)
        mean = np.mean(values)
        std = np.std(values)
        
        # 计算趋势
        if len(values) >= 14:
            recent_avg = np.mean(values[-7:])
            older_avg = np.mean(values[-14:-7])
            if recent_avg > older_avg * 1.1:
                trend = 'increasing'
                trend_rate = (recent_avg - older_avg) / older_avg
            elif recent_avg < older_avg * 0.9:
                trend = 'decreasing'
                trend_rate = (older_avg - recent_avg) / older_avg
            else:
                trend = 'stable'
                trend_rate = 0
        else:
            trend = 'unknown'
            trend_rate = 0
        
        # 生成预测（模拟）
        forecast = []
        lower = []
        upper = []
        
        for i in range(periods):
            # 简单预测：历史均值 + 随机波动 + 趋势
            base = mean * (1 + trend_rate * i / 30)
            noise = random.uniform(-std * 0.2, std * 0.2)
            pred = max(0, base + noise)
            forecast.append(round(pred, 2))
            lower.append(round(pred * 0.85, 2))
            upper.append(round(pred * 1.15, 2))
        
        return {
            'forecast': forecast,
            'confidence_lower': lower,
            'confidence_upper': upper,
            'trend': trend,
            'trend_rate': round(trend_rate, 4),
            'mean': round(mean, 2),
            'std': round(std, 2)
        }


class UsageAnalyzer:
    """用水分析引擎"""
    
    def __init__(self):
        pass
    
    def analyze_patterns(self, data: Dict) -> Dict:
        """
        分析用水模式
        
        维度:
        1. 时间模式: 日/周/月/季节
        2. 用水类型分布
        3. 峰谷分析
        4. 区域对比
        """
        usage_history = data.get('usage_history', [])
        user_type = data.get('user_type', 'residential')
        
        patterns = {}
        
        # 日模式
        if usage_history:
            daily_pattern = self._analyze_daily_pattern(usage_history)
            patterns['daily'] = daily_pattern
        
        # 周模式
        if len(usage_history) >= 7:
            weekly_pattern = self._analyze_weekly_pattern(usage_history)
            patterns['weekly'] = weekly_pattern
        
        # 季节性
        if len(usage_history) >= 30:
            seasonal = self._analyze_seasonal(usage_history)
            patterns['seasonal'] = seasonal
        
        # 用水类型特征
        type_features = self._analyze_type_features(usage_history, user_type)
        patterns['type_features'] = type_features
        
        return patterns
    
    def _analyze_daily_pattern(self, history: List[float]) -> Dict:
        """日用水模式"""
        values = np.array(history)
        return {
            'mean_daily': round(np.mean(values), 2),
            'max_daily': round(np.max(values), 2),
            'min_daily': round(np.min(values), 2),
            'variability': round(np.std(values) / max(np.mean(values), 1), 4)
        }
    
    def _analyze_weekly_pattern(self, history: List[float]) -> Dict:
        """周用水模式"""
        # 工作日vs周末
        if len(history) >= 14:
            weekdays = history[:5]
            weekends = history[5:7] if len(history) >= 7 else []
            weekday_avg = np.mean(weekdays) if weekdays else 0
            weekend_avg = np.mean(weekends) if weekends else 0
            
            return {
                'weekday_avg': round(weekday_avg, 2),
                'weekend_avg': round(weekend_avg, 2),
                'weekend_ratio': round(weekend_avg / max(weekday_avg, 1), 2)
            }
        return {}
    
    def _analyze_seasonal(self, history: List[float]) -> Dict:
        """季节性分析"""
        values = np.array(history[-30:])
        
        # 简化季节性检测
        first_half = np.mean(values[:15])
        second_half = np.mean(values[15:])
        
        return {
            'first_half_avg': round(first_half, 2),
            'second_half_avg': round(second_half, 2),
            'change_ratio': round((second_half - first_half) / max(first_half, 1), 4)
        }
    
    def _analyze_type_features(self, history: List[float], user_type: str) -> Dict:
        """用户类型特征"""
        avg = np.mean(history) if history else 0
        
        type_benchmarks = {
            'residential': {'expected_avg': 10, 'range': (5, 20)},
            'commercial': {'expected_avg': 50, 'range': (20, 100)},
            'industrial': {'expected_avg': 200, 'range': (50, 500)}
        }
        
        benchmark = type_benchmarks.get(user_type, {'expected_avg': 10, 'range': (0, 100)})
        deviation = (avg - benchmark['expected_avg']) / benchmark['expected_avg']
        
        return {
            'user_type': user_type,
            'actual_avg': round(avg, 2),
            'expected_avg': benchmark['expected_avg'],
            'deviation': round(deviation, 4),
            'in_range': benchmark['range'][0] <= avg <= benchmark['range'][1]
        }
    
    def generate_insights(self, patterns: Dict, forecast: Dict) -> List[Dict]:
        """生成洞察"""
        insights = []
        
        # 趋势洞察
        if forecast.get('trend') == 'increasing':
            insights.append({
                'type': 'trend',
                'severity': 'warning',
                'message': f"用水量呈上升趋势({forecast.get('trend_rate', 0)*100:.1f}%)，建议关注",
                'recommendation': '排查是否存在漏水或用水习惯变化'
            })
        elif forecast.get('trend') == 'decreasing':
            insights.append({
                'type': 'trend',
                'severity': 'info',
                'message': f"用水量呈下降趋势({forecast.get('trend_rate', 0)*100:.1f}%)",
                'recommendation': '可能是节水措施生效或人员减少'
            })
        
        # 模式洞察
        daily = patterns.get('daily', {})
        if daily.get('variability', 0) > 0.5:
            insights.append({
                'type': 'pattern',
                'severity': 'info',
                'message': f"用水波动较大(变异系数{daily.get('variability', 0):.2f})",
                'recommendation': '建议分析波动原因'
            })
        
        # 类型特征洞察
        type_features = patterns.get('type_features', {})
        if not type_features.get('in_range', True):
            insights.append({
                'type': 'anomaly',
                'severity': 'warning',
                'message': f"用水量偏离{type_features.get('user_type')}类型预期范围",
                'recommendation': '核实用户类型或排查异常'
            })
        
        return insights


class AnalysisAgent(BaseAgent):
    """
    分析智能体
    
    感知能力:
    - 感知历史用水数据
    - 感知区域用水统计
    - 感知预测模型输入
    
    决策能力:
    - 选择分析维度和模型
    - 识别趋势和模式
    - 生成洞察和建议
    
    执行能力:
    - 生成分析报告
    - 推送洞察通知
    - 触发相关智能体
    """

    def __init__(self, knowledge_base: KnowledgeBase = None):
        super().__init__(
            agent_id="analysis_agent",
            agent_name="分析智能体",
            description="负责用水数据分析、趋势预测和报告生成"
        )
        self.capabilities = [
            AgentCapability.PERCEIVE,
            AgentCapability.DECIDE,
            AgentCapability.EXECUTE,
            AgentCapability.LEARN
        ]
        
        self.knowledge_base = knowledge_base or KnowledgeBase()
        self.forecaster = TimeSeriesForecaster()
        self.analyzer = UsageAnalyzer()
        
        # 分析报告存储
        self.reports: List[Dict] = []

    def _on_start(self):
        logger.info("分析智能体: 初始化完成，分析引擎已就绪")

    # ==================== 感知 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """
        感知分析数据
        
        模式:
        1. single_meter: 单水表分析
        2. area: 区域分析
        3. forecast: 预测任务
        4. report: 报告生成
        """
        mode = context.get('mode', 'single_meter')
        
        if mode == 'single_meter':
            return self._perceive_single_meter(context)
        elif mode == 'area':
            return self._perceive_area(context)
        elif mode == 'forecast':
            return self._perceive_forecast(context)
        elif mode == 'report':
            return self._perceive_report(context)
        else:
            return PerceptionResult(confidence=0.0, metadata={'error': f'未知模式: {mode}'})

    def _perceive_single_meter(self, context: Dict) -> PerceptionResult:
        """单水表分析感知"""
        meter_id = context.get('meter_id', 'unknown')
        usage_history = context.get('usage_history', [])
        
        if not usage_history:
            usage_history = self._simulate_usage_history()
        
        perceived_data = {
            'mode': 'single_meter',
            'meter_id': meter_id,
            'usage_history': usage_history,
            'user_type': context.get('user_type', 'residential'),
            'analysis_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95,
            source='meter_data',
            metadata={'data_points': len(usage_history)}
        )

    def _perceive_area(self, context: Dict) -> PerceptionResult:
        """区域分析感知"""
        area_id = context.get('area_id', 'all')
        
        # 模拟区域数据
        meters_data = []
        for i in range(random.randint(50, 200)):
            meters_data.append({
                'meter_id': f'WM-{area_id}-{i:04d}',
                'usage_history': self._simulate_usage_history(30),
                'user_type': random.choice(['residential', 'commercial', 'industrial'])
            })
        
        perceived_data = {
            'mode': 'area',
            'area_id': area_id,
            'meters_data': meters_data,
            'total_meters': len(meters_data),
            'analysis_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.90,
            source='area_data',
            metadata={'area_size': len(meters_data)}
        )

    def _perceive_forecast(self, context: Dict) -> PerceptionResult:
        """预测任务感知"""
        history = context.get('usage_history', self._simulate_usage_history(60))
        periods = context.get('forecast_periods', 30)
        
        perceived_data = {
            'mode': 'forecast',
            'usage_history': history,
            'forecast_periods': periods,
            'analysis_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.90,
            source='forecast_input',
            metadata={'history_length': len(history), 'forecast_periods': periods}
        )

    def _perceive_report(self, context: Dict) -> PerceptionResult:
        """报告生成感知"""
        report_type = context.get('report_type', 'monthly')
        period = context.get('period', datetime.now().strftime('%Y-%m'))
        
        perceived_data = {
            'mode': 'report',
            'report_type': report_type,
            'period': period,
            'analysis_time': datetime.now().isoformat()
        }
        
        return PerceptionResult(
            data=perceived_data,
            confidence=0.95,
            source='report_request',
            metadata={'report_type': report_type}
        )

    # ==================== 决策 ====================

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """分析决策"""
        data = perception.data or {}
        mode = data.get('mode', 'single_meter')
        
        if mode == 'single_meter':
            return self._decide_single_meter(data)
        elif mode == 'area':
            return self._decide_area(data)
        elif mode == 'forecast':
            return self._decide_forecast(data)
        elif mode == 'report':
            return self._decide_report(data)
        else:
            return DecisionResult(action='unknown', confidence=0.0, reasoning='未知模式')

    def _decide_single_meter(self, data: Dict) -> DecisionResult:
        """单水表分析决策"""
        meter_id = data.get('meter_id')
        usage_history = data.get('usage_history', [])
        user_type = data.get('user_type', 'residential')
        
        # 模式分析
        patterns = self.analyzer.analyze_patterns(data)
        
        # 趋势预测
        forecast = self.forecaster.forecast(usage_history, periods=30)
        
        # 生成洞察
        insights = self.analyzer.generate_insights(patterns, forecast)
        
        return DecisionResult(
            action="generate_meter_report",
            params={
                'meter_id': meter_id,
                'user_type': user_type,
                'patterns': patterns,
                'forecast': forecast,
                'insights': insights,
                'summary': {
                    'avg_usage': round(np.mean(usage_history), 2) if usage_history else 0,
                    'trend': forecast.get('trend', 'unknown'),
                    'insight_count': len(insights)
                }
            },
            confidence=0.90,
            reasoning=f"水表{meter_id}: 日均{np.mean(usage_history):.1f}吨, 趋势{forecast.get('trend')}"
        )

    def _decide_area(self, data: Dict) -> DecisionResult:
        """区域分析决策"""
        area_id = data.get('area_id')
        meters_data = data.get('meters_data', [])
        
        # 区域统计
        total_usage = 0
        usage_by_type = defaultdict(float)
        anomaly_count = 0
        
        for meter in meters_data:
            history = meter.get('usage_history', [])
            meter_total = sum(history)
            total_usage += meter_total
            usage_by_type[meter.get('user_type', 'residential')] += meter_total
            
            # 检测异常
            if history and np.std(history) > np.mean(history) * 0.5:
                anomaly_count += 1
        
        return DecisionResult(
            action="generate_area_report",
            params={
                'area_id': area_id,
                'total_usage': round(total_usage, 2),
                'meter_count': len(meters_data),
                'avg_per_meter': round(total_usage / max(len(meters_data), 1), 2),
                'usage_by_type': dict(usage_by_type),
                'anomaly_count': anomaly_count,
                'anomaly_rate': round(anomaly_count / max(len(meters_data), 1), 4)
            },
            confidence=0.90,
            reasoning=f"区域{area_id}: {len(meters_data)}户, 总用量{total_usage:.1f}吨, 异常{anomaly_count}户"
        )

    def _decide_forecast(self, data: Dict) -> DecisionResult:
        """预测决策"""
        usage_history = data.get('usage_history', [])
        periods = data.get('forecast_periods', 30)
        
        forecast_result = self.forecaster.forecast(usage_history, periods)
        
        return DecisionResult(
            action="return_forecast",
            params={
                'forecast': forecast_result,
                'periods': periods,
                'history_length': len(usage_history)
            },
            confidence=forecast_result.get('trend') != 'unknown',
            reasoning=f"预测{periods}天: 趋势{forecast_result.get('trend')}, 日均{forecast_result.get('mean', 0):.1f}吨"
        )

    def _decide_report(self, data: Dict) -> DecisionResult:
        """报告决策"""
        report_type = data.get('report_type', 'monthly')
        period = data.get('period')
        
        # 模拟报告内容
        report_content = {
            'report_type': report_type,
            'period': period,
            'summary': {
                'total_usage': random.randint(10000, 50000),
                'total_revenue': random.randint(50000, 200000),
                'collection_rate': round(random.uniform(0.85, 0.98), 4),
                'anomaly_count': random.randint(10, 50),
                'meters_count': random.randint(1000, 5000)
            },
            'trends': {
                'usage_trend': random.choice(['increasing', 'stable', 'decreasing']),
                'revenue_trend': random.choice(['increasing', 'stable', 'decreasing'])
            },
            'recommendations': [
                '加强夜间异常用水检测',
                '优化商业用水费率结构',
                '推进智能水表覆盖率'
            ]
        }
        
        return DecisionResult(
            action="create_report",
            params=report_content,
            confidence=0.95,
            reasoning=f"生成{report_type}报告: 期间{period}"
        )

    # ==================== 执行 ====================

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """执行分析"""
        action = decision.action
        params = decision.params
        
        if action == "generate_meter_report":
            return self._execute_meter_report(params)
        elif action == "generate_area_report":
            return self._execute_area_report(params)
        elif action == "return_forecast":
            return self._execute_forecast(params)
        elif action == "create_report":
            return self._execute_create_report(params)
        else:
            return ExecutionResult(success=False, message=f"未知动作: {action}")

    def _execute_meter_report(self, params: Dict) -> ExecutionResult:
        """生成水表报告"""
        meter_id = params.get('meter_id')
        report_id = f"RPT-MTR-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        report = {
            'report_id': report_id,
            'type': 'meter_analysis',
            'meter_id': meter_id,
            **params,
            'created_at': datetime.now().isoformat()
        }
        
        self.reports.append(report)
        
        logger.info(f"分析智能体: 生成水表报告 [{report_id}] → {meter_id}")
        
        return ExecutionResult(
            success=True,
            data=report,
            message=f"水表{meter_id}分析报告已生成",
            next_actions=['notify_user', 'trigger_anomaly_check'] if params.get('insights') else []
        )

    def _execute_area_report(self, params: Dict) -> ExecutionResult:
        """生成区域报告"""
        area_id = params.get('area_id')
        report_id = f"RPT-AREA-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        report = {
            'report_id': report_id,
            'type': 'area_analysis',
            'area_id': area_id,
            **params,
            'created_at': datetime.now().isoformat()
        }
        
        self.reports.append(report)
        
        logger.info(f"分析智能体: 生成区域报告 [{report_id}] → {area_id}")
        
        return ExecutionResult(
            success=True,
            data=report,
            message=f"区域{area_id}分析报告已生成"
        )

    def _execute_forecast(self, params: Dict) -> ExecutionResult:
        """返回预测结果"""
        logger.info(f"分析智能体: 预测完成")
        
        return ExecutionResult(
            success=True,
            data=params.get('forecast'),
            message=f"预测完成: {params.get('periods')}天"
        )

    def _execute_create_report(self, params: Dict) -> ExecutionResult:
        """创建报告"""
        report_id = f"RPT-{params.get('report_type', 'monthly').upper()}-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        report = {
            'report_id': report_id,
            **params,
            'created_at': datetime.now().isoformat()
        }
        
        self.reports.append(report)
        
        logger.info(f"分析智能体: 创建{params.get('report_type')}报告 [{report_id}]")
        
        return ExecutionResult(
            success=True,
            data=report,
            message=f"{params.get('report_type')}报告已生成: {params.get('period')}"
        )

    # ==================== 辅助方法 ====================

    def _simulate_usage_history(self, days: int = 30) -> List[float]:
        """模拟历史用水数据"""
        base = random.uniform(5, 20)
        history = []
        for i in range(days):
            noise = random.uniform(-base * 0.3, base * 0.3)
            history.append(round(base + noise, 2))
        return history

    def get_analysis_stats(self) -> Dict:
        """获取分析统计"""
        return {
            'total_reports': len(self.reports),
            'by_type': {
                'meter': sum(1 for r in self.reports if 'meter' in r.get('type', '')),
                'area': sum(1 for r in self.reports if 'area' in r.get('type', '')),
                'monthly': sum(1 for r in self.reports if 'MONTHLY' in r.get('report_id', ''))
            }
        }
