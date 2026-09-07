"""
智能体基类 - 定义智能体的核心抽象
所有具体智能体（抄表/计费/异常/催缴/分析）均继承此基类
实现自主感知、自主决策、自主执行三大核心能力
"""
import uuid
import time
import threading
from enum import Enum
from typing import Any, Dict, List, Optional, Callable
from dataclasses import dataclass, field
from datetime import datetime
from loguru import logger


class AgentState(Enum):
    """智能体状态枚举"""
    IDLE = "idle"                    # 空闲
    PERCEIVING = "perceiving"        # 感知中
    DECIDING = "deciding"            # 决策中
    EXECUTING = "executing"          # 执行中
    SUSPENDED = "suspended"          # 挂起
    ERROR = "error"                  # 异常


class AgentCapability(Enum):
    """智能体能力枚举"""
    PERCEIVE = "perceive"            # 感知能力
    DECIDE = "decide"                # 决策能力
    EXECUTE = "execute"              # 执行能力
    LEARN = "learn"                  # 学习能力
    COLLABORATE = "collaborate"      # 协作能力


@dataclass
class AgentMessage:
    """智能体间通信消息"""
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    sender: str = ""
    receiver: str = ""               # 为空表示广播
    msg_type: str = ""               # 消息类型: command/event/query/response
    subject: str = ""                # 消息主题
    content: Any = None              # 消息内容
    priority: int = 5                # 优先级 1-10, 1最高
    timestamp: datetime = field(default_factory=datetime.now)
    correlation_id: str = ""         # 关联ID，用于请求-响应匹配
    metadata: Dict = field(default_factory=dict)

    def to_dict(self) -> Dict:
        return {
            'id': self.id,
            'sender': self.sender,
            'receiver': self.receiver,
            'msg_type': self.msg_type,
            'subject': self.subject,
            'content': self.content,
            'priority': self.priority,
            'timestamp': self.timestamp.isoformat(),
            'correlation_id': self.correlation_id,
            'metadata': self.metadata
        }


@dataclass
class PerceptionResult:
    """感知结果"""
    data: Any = None
    confidence: float = 0.0
    source: str = ""
    timestamp: datetime = field(default_factory=datetime.now)
    metadata: Dict = field(default_factory=dict)


@dataclass
class DecisionResult:
    """决策结果"""
    action: str = ""
    params: Dict = field(default_factory=dict)
    confidence: float = 0.0
    reasoning: str = ""              # 决策推理过程（可解释性）
    alternatives: List[Dict] = field(default_factory=list)  # 备选方案
    timestamp: datetime = field(default_factory=datetime.now)


@dataclass
class ExecutionResult:
    """执行结果"""
    success: bool = False
    data: Any = None
    message: str = ""
    next_actions: List[str] = field(default_factory=list)  # 触发的后续动作
    timestamp: datetime = field(default_factory=datetime.now)


class BaseAgent:
    """
    智能体基类 - 所有智能体的抽象父类
    
    核心设计理念:
    1. 自主感知(Perceive): 主动获取环境信息、数据变化、事件通知
    2. 自主决策(Decide): 基于感知结果和知识库进行推理判断
    3. 自主执行(Execute): 根据决策结果执行具体操作
    4. 持续学习(Learn): 从执行结果中学习，优化决策模型
    5. 协作通信(Collaborate): 与其他智能体协作完成复杂任务
    """

    def __init__(self, agent_id: str, agent_name: str, description: str = ""):
        # 基础属性
        self.agent_id = agent_id
        self.agent_name = agent_name
        self.description = description
        self.state = AgentState.IDLE
        self.capabilities: List[AgentCapability] = []
        
        # 消息系统
        self.message_queue: List[AgentMessage] = []
        self.message_handlers: Dict[str, Callable] = {}
        self.response_futures: Dict[str, Any] = {}
        
        # 知识与记忆
        self.knowledge: Dict = {}
        self.working_memory: Dict = {}
        self.episodic_memory: List[Dict] = []  # 经验记忆
        
        # 统计与监控
        self.stats = {
            'perceive_count': 0,
            'decide_count': 0,
            'execute_count': 0,
            'error_count': 0,
            'last_active': None,
            'total_execution_time': 0.0
        }
        
        # 配置
        self.config: Dict = {}
        self.confidence_threshold = 0.95
        self.max_retry = 3
        
        # 线程控制
        self._running = False
        self._lock = threading.Lock()
        self._event_callback: Optional[Callable] = None
        
        # 注册默认消息处理器
        self._register_default_handlers()

    def _register_default_handlers(self):
        """注册默认消息处理器"""
        self.message_handlers = {
            'command': self._handle_command,
            'event': self._handle_event,
            'query': self._handle_query,
            'response': self._handle_response,
        }

    # ==================== 生命周期管理 ====================

    def start(self):
        """启动智能体"""
        if self._running:
            logger.warning(f"智能体 [{self.agent_name}] 已在运行中")
            return
        self._running = True
        self.state = AgentState.IDLE
        logger.info(f"智能体 [{self.agent_name}] 启动成功, 能力: {[c.value for c in self.capabilities]}")
        self._on_start()

    def stop(self):
        """停止智能体"""
        self._running = False
        self.state = AgentState.SUSPENDED
        logger.info(f"智能体 [{self.agent_name}] 已停止")

    def _on_start(self):
        """子类重写 - 启动时的初始化逻辑"""
        pass

    # ==================== 核心三段式: 感知→决策→执行 ====================

    def perceive(self, context: Dict = None) -> PerceptionResult:
        """
        自主感知 - 主动获取环境信息
        子类必须重写此方法实现具体感知逻辑
        """
        self._set_state(AgentState.PERCEIVING)
        start_time = time.time()
        try:
            result = self._do_perceive(context or {})
            self.stats['perceive_count'] += 1
            logger.debug(f"智能体 [{self.agent_name}] 感知完成, 置信度: {result.confidence:.2f}")
            return result
        except Exception as e:
            self.stats['error_count'] += 1
            logger.error(f"智能体 [{self.agent_name}] 感知异常: {e}")
            return PerceptionResult(confidence=0.0, metadata={'error': str(e)})
        finally:
            self._update_execution_time(start_time)

    def decide(self, perception: PerceptionResult) -> DecisionResult:
        """
        自主决策 - 基于感知结果进行推理判断
        子类必须重写此方法实现具体决策逻辑
        """
        self._set_state(AgentState.DECIDING)
        start_time = time.time()
        try:
            result = self._do_decide(perception)
            self.stats['decide_count'] += 1
            logger.debug(f"智能体 [{self.agent_name}] 决策完成, 动作: {result.action}, 置信度: {result.confidence:.2f}")
            return result
        except Exception as e:
            self.stats['error_count'] += 1
            logger.error(f"智能体 [{self.agent_name}] 决策异常: {e}")
            return DecisionResult(action='error', reasoning=str(e), confidence=0.0)
        finally:
            self._update_execution_time(start_time)

    def execute(self, decision: DecisionResult) -> ExecutionResult:
        """
        自主执行 - 根据决策结果执行操作
        子类必须重写此方法实现具体执行逻辑
        """
        self._set_state(AgentState.EXECUTING)
        start_time = time.time()
        try:
            result = self._do_execute(decision)
            self.stats['execute_count'] += 1
            # 记录经验
            self._record_experience(decision, result)
            logger.debug(f"智能体 [{self.agent_name}] 执行完成, 成功: {result.success}")
            return result
        except Exception as e:
            self.stats['error_count'] += 1
            logger.error(f"智能体 [{self.agent_name}] 执行异常: {e}")
            return ExecutionResult(success=False, message=str(e))
        finally:
            self._set_state(AgentState.IDLE)
            self._update_execution_time(start_time)

    def run_cycle(self, context: Dict = None) -> ExecutionResult:
        """
        执行一个完整的 感知→决策→执行 周期
        """
        logger.info(f"智能体 [{self.agent_name}] 开始执行周期")
        perception = self.perceive(context)
        decision = self.decide(perception)
        execution = self.execute(decision)
        self.stats['last_active'] = datetime.now().isoformat()
        return execution

    # ==================== 子类必须重写的抽象方法 ====================

    def _do_perceive(self, context: Dict) -> PerceptionResult:
        """具体感知逻辑 - 子类重写"""
        raise NotImplementedError(f"智能体 [{self.agent_name}] 未实现感知方法")

    def _do_decide(self, perception: PerceptionResult) -> DecisionResult:
        """具体决策逻辑 - 子类重写"""
        raise NotImplementedError(f"智能体 [{self.agent_name}] 未实现决策方法")

    def _do_execute(self, decision: DecisionResult) -> ExecutionResult:
        """具体执行逻辑 - 子类重写"""
        raise NotImplementedError(f"智能体 [{self.agent_name}] 未实现执行方法")

    # ==================== 消息通信 ====================

    def send_message(self, message: AgentMessage):
        """发送消息给其他智能体"""
        message.sender = self.agent_id
        message.timestamp = datetime.now()
        if self._event_callback:
            self._event_callback('message_sent', message.to_dict())
        logger.info(f"智能体 [{self.agent_name}] 发送消息 → [{message.receiver}], 主题: {message.subject}")

    def receive_message(self, message: AgentMessage):
        """接收消息"""
        with self._lock:
            self.message_queue.append(message)
        logger.debug(f"智能体 [{self.agent_name}] 收到消息 ← [{message.sender}], 主题: {message.subject}")

    def process_messages(self):
        """处理消息队列"""
        with self._lock:
            messages = sorted(self.message_queue, key=lambda m: m.priority)
            self.message_queue.clear()
        
        for msg in messages:
            handler = self.message_handlers.get(msg.msg_type)
            if handler:
                try:
                    handler(msg)
                except Exception as e:
                    logger.error(f"智能体 [{self.agent_name}] 处理消息异常: {e}")

    def _handle_command(self, msg: AgentMessage):
        """处理命令消息"""
        logger.info(f"智能体 [{self.agent_name}] 处理命令: {msg.subject}")
        # 命令触发感知-决策-执行周期
        context = msg.content or {}
        context['command_source'] = msg.sender
        result = self.run_cycle(context)
        # 发送响应
        response = AgentMessage(
            receiver=msg.sender,
            msg_type='response',
            subject=f"re:{msg.subject}",
            content=result.__dict__ if hasattr(result, '__dict__') else str(result),
            correlation_id=msg.id
        )
        self.send_message(response)

    def _handle_event(self, msg: AgentMessage):
        """处理事件消息"""
        logger.info(f"智能体 [{self.agent_name}] 处理事件: {msg.subject}")
        self._on_event(msg)

    def _handle_query(self, msg: AgentMessage):
        """处理查询消息"""
        logger.info(f"智能体 [{self.agent_name}] 处理查询: {msg.subject}")
        result = self._on_query(msg)
        response = AgentMessage(
            receiver=msg.sender,
            msg_type='response',
            subject=f"re:{msg.subject}",
            content=result,
            correlation_id=msg.id
        )
        self.send_message(response)

    def _handle_response(self, msg: AgentMessage):
        """处理响应消息"""
        if msg.correlation_id in self.response_futures:
            self.response_futures[msg.correlation_id] = msg

    def _on_event(self, msg: AgentMessage):
        """子类重写 - 事件处理"""
        pass

    def _on_query(self, msg: AgentMessage) -> Any:
        """子类重写 - 查询处理"""
        return {'status': 'ok', 'agent': self.agent_name}

    # ==================== 学习与记忆 ====================

    def _record_experience(self, decision: DecisionResult, execution: ExecutionResult):
        """记录经验到情景记忆"""
        experience = {
            'timestamp': datetime.now().isoformat(),
            'perception_summary': decision.reasoning[:100] if decision.reasoning else '',
            'action': decision.action,
            'params': decision.params,
            'success': execution.success,
            'result_message': execution.message
        }
        self.episodic_memory.append(experience)
        # 保持记忆在合理范围
        if len(self.episodic_memory) > 1000:
            self.episodic_memory = self.episodic_memory[-500:]

    def learn_from_experience(self):
        """从经验中学习 - 子类可重写"""
        if not self.episodic_memory:
            return
        recent = self.episodic_memory[-50:]
        success_rate = sum(1 for e in recent if e['success']) / len(recent)
        logger.info(f"智能体 [{self.agent_name}] 学习: 近期成功率 {success_rate:.2%}")

    # ==================== 辅助方法 ====================

    def _set_state(self, new_state: AgentState):
        """设置状态"""
        old_state = self.state
        self.state = new_state
        if self._event_callback:
            self._event_callback('state_change', {
                'agent_id': self.agent_id,
                'old_state': old_state.value,
                'new_state': new_state.value
            })

    def _update_execution_time(self, start_time: float):
        """更新执行时间统计"""
        elapsed = time.time() - start_time
        self.stats['total_execution_time'] += elapsed

    def set_event_callback(self, callback: Callable):
        """设置事件回调（由协调器调用）"""
        self._event_callback = callback

    def update_config(self, config: Dict):
        """更新配置"""
        self.config.update(config)
        if 'confidence_threshold' in config:
            self.confidence_threshold = config['confidence_threshold']

    def get_status(self) -> Dict:
        """获取智能体状态"""
        return {
            'agent_id': self.agent_id,
            'agent_name': self.agent_name,
            'description': self.description,
            'state': self.state.value,
            'capabilities': [c.value for c in self.capabilities],
            'stats': self.stats,
            'message_queue_size': len(self.message_queue),
            'knowledge_size': len(self.knowledge),
            'experience_size': len(self.episodic_memory),
            'config': self.config
        }

    def __repr__(self):
        return f"<Agent {self.agent_name}({self.agent_id}) state={self.state.value}>"
