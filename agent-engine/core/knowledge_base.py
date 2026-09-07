"""
智能体共享知识库
提供智能体间的知识共享、规则存储和经验积累
"""
import json
import threading
from typing import Any, Dict, List, Optional
from datetime import datetime
from loguru import logger


class KnowledgeBase:
    """
    知识库 - 智能体共享的知识中心
    
    知识类型:
    1. 事实知识: 水表信息、用户信息、费率规则等
    2. 规则知识: 业务规则、异常判定规则、催缴策略等
    3. 经验知识: 历史处理经验、成功率统计等
    4. 模型知识: AI模型参数、阈值配置等
    """

    def __init__(self):
        self._lock = threading.Lock()
        
        # 事实知识
        self.facts: Dict[str, Any] = {}
        
        # 规则知识
        self.rules: Dict[str, Dict] = {}
        
        # 经验知识
        self.experiences: Dict[str, List[Dict]] = {}
        
        # 模型知识
        self.model_params: Dict[str, Any] = {}
        
        # 知识版本控制
        self.versions: Dict[str, List[Dict]] = {}
        
        # 初始化内置规则
        self._init_builtin_rules()

    def _init_builtin_rules(self):
        """初始化内置业务规则"""
        # 异常检测规则
        self.rules['anomaly_detection'] = {
            'sudden_increase': {
                'description': '用水量突增检测',
                'condition': 'current_usage > avg_usage * 3',
                'threshold': 3.0,
                'severity': 'high'
            },
            'zero_usage_long': {
                'description': '长期零用水检测',
                'condition': 'zero_usage_days > 30',
                'threshold': 30,
                'severity': 'medium'
            },
            'night_usage': {
                'description': '夜间异常用水检测',
                'condition': 'night_usage > day_avg * 0.5',
                'threshold': 0.5,
                'severity': 'medium'
            },
            'gradual_increase': {
                'description': '用水量持续增长检测',
                'condition': 'monthly_increase_rate > 0.2 for 3 months',
                'threshold': 0.2,
                'severity': 'low'
            }
        }
        
        # 催缴策略规则
        self.rules['collection_strategy'] = {
            'gentle_reminder': {
                'description': '温和提醒',
                'condition': 'overdue_days <= 7',
                'action': 'send_sms',
                'template': 'friendly_reminder'
            },
            'formal_notice': {
                'description': '正式通知',
                'condition': '7 < overdue_days <= 30',
                'action': 'send_letter',
                'template': 'formal_notice'
            },
            'phone_call': {
                'description': '电话催缴',
                'condition': '30 < overdue_days <= 60',
                'action': 'phone_call',
                'template': 'phone_script'
            },
            'water_restriction': {
                'description': '限制供水',
                'condition': 'overdue_days > 60',
                'action': 'restrict_water',
                'template': 'restriction_notice'
            }
        }
        
        # 费率规则
        self.rules['pricing'] = {
            'residential_ladder': {
                'description': '居民阶梯水价',
                'ladders': [
                    {'min': 0, 'max': 180, 'price': 2.07},    # 第一阶梯
                    {'min': 181, 'max': 260, 'price': 3.10},   # 第二阶梯
                    {'min': 261, 'max': None, 'price': 4.65},  # 第三阶梯
                ],
                'unit': '吨/月/户'
            },
            'commercial': {
                'description': '商业用水',
                'price': 4.50,
                'unit': '吨/月'
            },
            'industrial': {
                'description': '工业用水',
                'price': 5.80,
                'unit': '吨/月'
            }
        }
        
        # 抄表规则
        self.rules['meter_reading'] = {
            'confidence_threshold': {
                'description': 'AI识别置信度阈值',
                'auto_accept': 0.95,      # 自动接受
                'human_review': 0.80,     # 人工复核
                'reject': 0.80            # 低于此值拒绝
            },
            'reading_validation': {
                'description': '读数合理性校验',
                'max_change_rate': 50,    # 最大变化率(吨/月)
                'min_reading': 0,         # 最小读数
                'monotonic_increase': True # 读数应单调递增
            }
        }

    # ==================== 事实知识操作 ====================

    def set_fact(self, key: str, value: Any, source: str = ""):
        """设置事实"""
        with self._lock:
            old_value = self.facts.get(key)
            self.facts[key] = value
            # 记录版本
            if key not in self.versions:
                self.versions[key] = []
            self.versions[key].append({
                'value': value,
                'source': source,
                'timestamp': datetime.now().isoformat(),
                'previous': old_value
            })
            logger.debug(f"知识库: 更新事实 [{key}], 来源: [{source}]")

    def get_fact(self, key: str, default: Any = None) -> Any:
        """获取事实"""
        return self.facts.get(key, default)

    def query_facts(self, pattern: str) -> Dict[str, Any]:
        """模糊查询事实"""
        with self._lock:
            return {k: v for k, v in self.facts.items() if pattern in k}

    # ==================== 规则知识操作 ====================

    def add_rule(self, rule_category: str, rule_name: str, rule_def: Dict):
        """添加规则"""
        with self._lock:
            if rule_category not in self.rules:
                self.rules[rule_category] = {}
            self.rules[rule_category][rule_name] = rule_def
            logger.info(f"知识库: 添加规则 [{rule_category}.{rule_name}]")

    def get_rule(self, rule_category: str, rule_name: str = None) -> Optional[Dict]:
        """获取规则"""
        category = self.rules.get(rule_category, {})
        if rule_name:
            return category.get(rule_name)
        return category

    def evaluate_rule(self, rule_category: str, rule_name: str, context: Dict) -> Dict:
        """
        评估规则是否匹配
        返回: {'matched': bool, 'action': str, 'params': Dict}
        """
        rule = self.get_rule(rule_category, rule_name)
        if not rule:
            return {'matched': False, 'reason': 'rule_not_found'}
        
        # 简单规则评估引擎
        condition = rule.get('condition', '')
        try:
            # 安全的条件评估
            result = self._safe_eval_condition(condition, context)
            return {
                'matched': result,
                'action': rule.get('action', ''),
                'params': rule,
                'rule_name': rule_name
            }
        except Exception as e:
            return {'matched': False, 'reason': f'eval_error: {e}'}

    def _safe_eval_condition(self, condition: str, context: Dict) -> bool:
        """安全评估条件表达式"""
        # 替换上下文变量
        eval_str = condition
        for key, value in context.items():
            if isinstance(value, (int, float)):
                eval_str = eval_str.replace(key, str(value))
        # 只允许安全的比较运算
        allowed_ops = ['>', '<', '>=', '<=', '==', '!=', 'and', 'or', 'not']
        for op in allowed_ops:
            if op in eval_str:
                break
        else:
            return False
        try:
            return bool(eval(eval_str, {"__builtins__": {}}, {}))
        except:
            return False

    # ==================== 经验知识操作 ====================

    def record_experience(self, agent_id: str, experience: Dict):
        """记录经验"""
        with self._lock:
            if agent_id not in self.experiences:
                self.experiences[agent_id] = []
            experience['timestamp'] = datetime.now().isoformat()
            self.experiences[agent_id].append(experience)
            # 限制经验数量
            if len(self.experiences[agent_id]) > 500:
                self.experiences[agent_id] = self.experiences[agent_id][-300:]

    def get_experiences(self, agent_id: str, limit: int = 50) -> List[Dict]:
        """获取经验"""
        return self.experiences.get(agent_id, [])[-limit:]

    def get_success_rate(self, agent_id: str, action: str = None) -> float:
        """获取经验成功率"""
        exps = self.experiences.get(agent_id, [])
        if not exps:
            return 0.0
        if action:
            exps = [e for e in exps if e.get('action') == action]
        if not exps:
            return 0.0
        return sum(1 for e in exps if e.get('success', False)) / len(exps)

    # ==================== 模型知识操作 ====================

    def update_model_params(self, model_name: str, params: Dict):
        """更新模型参数"""
        with self._lock:
            self.model_params[model_name] = {
                'params': params,
                'updated_at': datetime.now().isoformat()
            }
            logger.info(f"知识库: 更新模型参数 [{model_name}]")

    def get_model_params(self, model_name: str) -> Optional[Dict]:
        """获取模型参数"""
        entry = self.model_params.get(model_name)
        return entry['params'] if entry else None

    # ==================== 导出/导入 ====================

    def export_knowledge(self) -> Dict:
        """导出全部知识"""
        with self._lock:
            return {
                'facts': self.facts,
                'rules': self.rules,
                'experiences': self.experiences,
                'model_params': self.model_params,
                'exported_at': datetime.now().isoformat()
            }

    def import_knowledge(self, data: Dict):
        """导入知识"""
        with self._lock:
            if 'facts' in data:
                self.facts.update(data['facts'])
            if 'rules' in data:
                self.rules.update(data['rules'])
            if 'experiences' in data:
                self.experiences.update(data['experiences'])
            if 'model_params' in data:
                self.model_params.update(data['model_params'])
        logger.info("知识库: 知识导入完成")

    def get_summary(self) -> Dict:
        """获取知识库摘要"""
        return {
            'facts_count': len(self.facts),
            'rules_categories': list(self.rules.keys()),
            'rules_count': sum(len(v) for v in self.rules.values()),
            'experiences_agents': list(self.experiences.keys()),
            'experiences_count': sum(len(v) for v in self.experiences.values()),
            'models_count': len(self.model_params)
        }
