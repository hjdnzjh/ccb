"""
智能体协调器 (Agent Orchestrator)
负责多智能体的注册、调度、协作、通信和生命周期管理
是整个多智能体系统的"大脑"
"""
import time
import threading
from typing import Any, Dict, List, Optional, Callable
from datetime import datetime
from enum import Enum
from collections import defaultdict
from loguru import logger

from .base_agent import BaseAgent, AgentMessage, AgentState


class TaskPriority(Enum):
    """任务优先级"""
    CRITICAL = 1    # 紧急：漏水/偷水等严重异常
    HIGH = 2        # 高：欠费催缴、设备故障
    NORMAL = 3      # 普通：日常抄表、账单生成
    LOW = 4         # 低：数据分析、报告生成


class TaskStatus(Enum):
    """任务状态"""
    PENDING = "pending"
    ASSIGNED = "assigned"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class Task:
    """协调器管理的任务"""
    def __init__(self, task_id: str, task_type: str, description: str,
                 priority: TaskPriority = TaskPriority.NORMAL,
                 context: Dict = None, required_agents: List[str] = None):
        self.task_id = task_id
        self.task_type = task_type
        self.description = description
        self.priority = priority
        self.context = context or {}
        self.required_agents = required_agents or []
        self.assigned_agents: List[str] = []
        self.status = TaskStatus.PENDING
        self.result: Any = None
        self.created_at = datetime.now()
        self.started_at: Optional[datetime] = None
        self.completed_at: Optional[datetime] = None
        self.sub_tasks: List['Task'] = []
        self.parent_task_id: Optional[str] = None

    def to_dict(self) -> Dict:
        return {
            'task_id': self.task_id,
            'task_type': self.task_type,
            'description': self.description,
            'priority': self.priority.value,
            'status': self.status.value,
            'assigned_agents': self.assigned_agents,
            'created_at': self.created_at.isoformat(),
            'result': str(self.result) if self.result else None
        }


class WorkflowStep:
    """工作流步骤"""
    def __init__(self, step_id: str, agent_id: str, action: str,
                 params: Dict = None, condition: str = None,
                 next_steps: List[str] = None):
        self.step_id = step_id
        self.agent_id = agent_id
        self.action = action
        self.params = params or {}
        self.condition = condition          # 执行条件表达式
        self.next_steps = next_steps or []  # 下一步骤ID列表
        self.status = 'pending'
        self.result: Any = None


class Workflow:
    """工作流定义 - 编排多智能体协作流程"""
    def __init__(self, workflow_id: str, name: str, description: str = ""):
        self.workflow_id = workflow_id
        self.name = name
        self.description = description
        self.steps: Dict[str, WorkflowStep] = {}
        self.start_step_id: Optional[str] = None
        self.current_step_id: Optional[str] = None
        self.status = 'pending'
        self.context: Dict = {}
        self.results: Dict[str, Any] = {}

    def add_step(self, step: WorkflowStep, is_start: bool = False):
        """添加步骤"""
        self.steps[step.step_id] = step
        if is_start:
            self.start_step_id = step.step_id

    def to_dict(self) -> Dict:
        return {
            'workflow_id': self.workflow_id,
            'name': self.name,
            'status': self.status,
            'current_step': self.current_step_id,
            'steps': {sid: {'agent': s.agent_id, 'action': s.action, 'status': s.status}
                      for sid, s in self.steps.items()}
        }


class AgentOrchestrator:
    """
    智能体协调器
    
    核心职责:
    1. 智能体注册与管理
    2. 任务调度与分配
    3. 智能体间消息路由
    4. 工作流编排与执行
    5. 全局状态监控
    6. 冲突检测与解决
    """

    def __init__(self):
        # 智能体注册表
        self.agents: Dict[str, BaseAgent] = {}
        self.agent_capabilities: Dict[str, List[str]] = defaultdict(list)
        
        # 任务管理
        self.tasks: Dict[str, Task] = {}
        self.task_queue: List[Task] = []
        
        # 工作流管理
        self.workflows: Dict[str, Workflow] = {}
        self.workflow_templates: Dict[str, Dict] = {}
        
        # 消息路由
        self.message_bus: List[AgentMessage] = []
        self.subscription_map: Dict[str, List[str]] = defaultdict(list)  # topic -> [agent_ids]
        
        # 全局知识库
        self.global_knowledge: Dict = {}
        
        # 事件监听
        self.event_listeners: List[Callable] = []
        
        # 运行控制
        self._running = False
        self._lock = threading.Lock()
        self._dispatch_thread: Optional[threading.Thread] = None
        
        # 统计
        self.stats = {
            'tasks_created': 0,
            'tasks_completed': 0,
            'tasks_failed': 0,
            'messages_routed': 0,
            'workflows_executed': 0,
            'start_time': None
        }

        # 注册内置工作流模板
        self._register_builtin_workflows()

    # ==================== 智能体管理 ====================

    def register_agent(self, agent: BaseAgent):
        """注册智能体"""
        self.agents[agent.agent_id] = agent
        for cap in agent.capabilities:
            self.agent_capabilities[cap.value].append(agent.agent_id)
        # 设置事件回调
        agent.set_event_callback(self._on_agent_event)
        logger.info(f"协调器: 注册智能体 [{agent.agent_name}], ID: {agent.agent_id}, "
                    f"能力: {[c.value for c in agent.capabilities]}")

    def unregister_agent(self, agent_id: str):
        """注销智能体"""
        if agent_id in self.agents:
            agent = self.agents.pop(agent_id)
            for cap in agent.capabilities:
                if agent_id in self.agent_capabilities[cap.value]:
                    self.agent_capabilities[cap.value].remove(agent_id)
            logger.info(f"协调器: 注销智能体 [{agent.agent_name}]")

    def get_agent(self, agent_id: str) -> Optional[BaseAgent]:
        """获取智能体"""
        return self.agents.get(agent_id)

    def get_agents_by_capability(self, capability: str) -> List[BaseAgent]:
        """根据能力查找智能体"""
        agent_ids = self.agent_capabilities.get(capability, [])
        return [self.agents[aid] for aid in agent_ids if aid in self.agents]

    # ==================== 任务调度 ====================

    def create_task(self, task_type: str, description: str,
                    priority: TaskPriority = TaskPriority.NORMAL,
                    context: Dict = None, required_agents: List[str] = None) -> Task:
        """创建任务"""
        task_id = f"task_{int(time.time() * 1000)}"
        task = Task(task_id, task_type, description, priority, context, required_agents)
        self.tasks[task_id] = task
        self.task_queue.append(task)
        self.stats['tasks_created'] += 1
        logger.info(f"协调器: 创建任务 [{task_id}], 类型: {task_type}, 优先级: {priority.name}")
        self._emit_event('task_created', task.to_dict())
        return task

    def dispatch_task(self, task: Task) -> bool:
        """调度任务到合适的智能体"""
        # 根据任务类型和能力匹配智能体
        candidate_agents = self._find_capable_agents(task)
        if not candidate_agents:
            logger.warning(f"协调器: 无可用智能体处理任务 [{task.task_id}]")
            return False
        
        # 选择最优智能体（当前空闲的优先）
        selected = self._select_best_agent(candidate_agents)
        if not selected:
            logger.warning(f"协调器: 所有候选智能体均忙碌，任务 [{task.task_id}] 等待")
            return False
        
        # 分配任务
        task.assigned_agents.append(selected.agent_id)
        task.status = TaskStatus.ASSIGNED
        
        # 发送命令消息给智能体
        msg = AgentMessage(
            sender='orchestrator',
            receiver=selected.agent_id,
            msg_type='command',
            subject=task.task_type,
            content=task.context,
            priority=1 if task.priority == TaskPriority.CRITICAL else 5
        )
        selected.receive_message(msg)
        
        logger.info(f"协调器: 任务 [{task.task_id}] 分配给智能体 [{selected.agent_name}]")
        self._emit_event('task_assigned', task.to_dict())
        return True

    def _find_capable_agents(self, task: Task) -> List[BaseAgent]:
        """查找能处理该任务的智能体"""
        # 任务类型到能力的映射
        task_capability_map = {
            'meter_reading': 'perceive',       # 抄表任务需要感知能力
            'billing': 'execute',              # 计费任务需要执行能力
            'anomaly_detect': 'perceive',      # 异常检测需要感知能力
            'collection': 'execute',           # 催缴任务需要执行能力
            'analysis': 'decide',              # 分析任务需要决策能力
        }
        required_cap = task_capability_map.get(task.task_type, 'perceive')
        agents = self.get_agents_by_capability(required_cap)
        
        # 如果任务指定了需要的智能体
        if task.required_agents:
            agents = [a for a in agents if a.agent_id in task.required_agents]
        
        return agents

    def _select_best_agent(self, candidates: List[BaseAgent]) -> Optional[BaseAgent]:
        """选择最优智能体"""
        # 优先选择空闲的
        idle_agents = [a for a in candidates if a.state == AgentState.IDLE]
        if idle_agents:
            # 在空闲智能体中选择经验最丰富的（执行次数最多）
            return max(idle_agents, key=lambda a: a.stats['execute_count'])
        return None

    # ==================== 工作流编排 ====================

    def _register_builtin_workflows(self):
        """注册内置工作流模板"""
        # 抄表→计费→缴费 完整工作流
        self.workflow_templates['reading_billing_workflow'] = {
            'name': '抄表计费工作流',
            'description': '自动抄表→生成账单→推送通知',
            'steps': [
                {'step_id': 'read', 'agent_id': 'meter_reading_agent', 'action': 'read_meter'},
                {'step_id': 'validate', 'agent_id': 'meter_reading_agent', 'action': 'validate_reading'},
                {'step_id': 'bill', 'agent_id': 'billing_agent', 'action': 'generate_bill'},
                {'step_id': 'notify', 'agent_id': 'collection_agent', 'action': 'send_notification'},
            ]
        }
        # 异常检测→预警→派单 工作流
        self.workflow_templates['anomaly_handling_workflow'] = {
            'name': '异常处理工作流',
            'description': '异常检测→分级研判→预警通知→派发工单',
            'steps': [
                {'step_id': 'detect', 'agent_id': 'anomaly_agent', 'action': 'detect_anomaly'},
                {'step_id': 'assess', 'agent_id': 'anomaly_agent', 'action': 'assess_severity'},
                {'step_id': 'alert', 'agent_id': 'anomaly_agent', 'action': 'send_alert'},
                {'step_id': 'dispatch', 'agent_id': 'anomaly_agent', 'action': 'dispatch_work_order'},
            ]
        }
        # 催缴工作流
        self.workflow_templates['collection_workflow'] = {
            'name': '智能催缴工作流',
            'description': '欠费识别→用户画像→策略选择→执行催缴',
            'steps': [
                {'step_id': 'identify', 'agent_id': 'collection_agent', 'action': 'identify_overdue'},
                {'step_id': 'profile', 'agent_id': 'collection_agent', 'action': 'analyze_user_profile'},
                {'step_id': 'strategy', 'agent_id': 'collection_agent', 'action': 'select_strategy'},
                {'step_id': 'execute', 'agent_id': 'collection_agent', 'action': 'execute_collection'},
            ]
        }

    def create_workflow(self, template_name: str, context: Dict = None) -> Workflow:
        """从模板创建工作流"""
        template = self.workflow_templates.get(template_name)
        if not template:
            raise ValueError(f"工作流模板 [{template_name}] 不存在")
        
        workflow_id = f"wf_{int(time.time() * 1000)}"
        workflow = Workflow(workflow_id, template['name'], template['description'])
        workflow.context = context or {}
        
        for i, step_def in enumerate(template['steps']):
            step = WorkflowStep(
                step_id=step_def['step_id'],
                agent_id=step_def['agent_id'],
                action=step_def['action'],
                next_steps=[template['steps'][i+1]['step_id']] if i < len(template['steps'])-1 else []
            )
            workflow.add_step(step, is_start=(i == 0))
        
        self.workflows[workflow_id] = workflow
        logger.info(f"协调器: 创建工作流 [{workflow.name}], ID: {workflow_id}")
        return workflow

    def execute_workflow(self, workflow: Workflow) -> Dict:
        """执行工作流"""
        workflow.status = 'running'
        workflow.current_step_id = workflow.start_step_id
        self.stats['workflows_executed'] += 1
        
        logger.info(f"协调器: 开始执行工作流 [{workflow.name}]")
        self._emit_event('workflow_started', workflow.to_dict())
        
        while workflow.current_step_id:
            step = workflow.steps.get(workflow.current_step_id)
            if not step:
                break
            
            # 获取执行智能体
            agent = self.agents.get(step.agent_id)
            if not agent:
                logger.error(f"协调器: 工作流步骤 [{step.step_id}] 的智能体 [{step.agent_id}] 不存在")
                workflow.status = 'failed'
                break
            
            # 执行步骤
            logger.info(f"协调器: 工作流步骤 [{step.step_id}] → 智能体 [{agent.agent_name}]")
            step_context = {**workflow.context, **workflow.results, 'action': step.action}
            step_context.update(step.params)
            
            try:
                result = agent.run_cycle(step_context)
                step.status = 'completed'
                step.result = result
                workflow.results[step.step_id] = {
                    'success': result.success,
                    'data': result.data,
                    'message': result.message
                }
                
                # 判断是否继续
                if not result.success:
                    logger.warning(f"协调器: 工作流步骤 [{step.step_id}] 执行失败: {result.message}")
                    workflow.status = 'failed'
                    break
                
                # 进入下一步
                if step.next_steps:
                    workflow.current_step_id = step.next_steps[0]
                else:
                    workflow.current_step_id = None
                    
            except Exception as e:
                logger.error(f"协调器: 工作流步骤 [{step.step_id}] 异常: {e}")
                step.status = 'failed'
                workflow.status = 'failed'
                break
        
        if workflow.status != 'failed':
            workflow.status = 'completed'
        
        logger.info(f"协调器: 工作流 [{workflow.name}] 执行完成, 状态: {workflow.status}")
        self._emit_event('workflow_completed', workflow.to_dict())
        return workflow.results

    # ==================== 消息路由 ====================

    def route_message(self, message: AgentMessage):
        """路由消息到目标智能体"""
        if message.receiver and message.receiver in self.agents:
            # 点对点消息
            self.agents[message.receiver].receive_message(message)
            self.stats['messages_routed'] += 1
        elif not message.receiver:
            # 广播消息
            for agent in self.agents.values():
                if agent.agent_id != message.sender:
                    agent.receive_message(message)
            self.stats['messages_routed'] += len(self.agents)
        else:
            logger.warning(f"协调器: 消息目标 [{message.receiver}] 不存在")

    def subscribe(self, topic: str, agent_id: str):
        """订阅主题"""
        self.subscription_map[topic].append(agent_id)
        logger.info(f"协调器: 智能体 [{agent_id}] 订阅主题 [{topic}]")

    def publish(self, topic: str, message: AgentMessage):
        """发布主题消息"""
        subscribers = self.subscription_map.get(topic, [])
        for agent_id in subscribers:
            if agent_id in self.agents:
                self.agents[agent_id].receive_message(message)
        self.stats['messages_routed'] += len(subscribers)

    # ==================== 全局协调 ====================

    def start(self):
        """启动协调器"""
        self._running = True
        self.stats['start_time'] = datetime.now().isoformat()
        # 启动所有已注册的智能体
        for agent in self.agents.values():
            agent.start()
        # 启动任务调度线程
        self._dispatch_thread = threading.Thread(target=self._dispatch_loop, daemon=True)
        self._dispatch_thread.start()
        logger.info("协调器: 启动完成，所有智能体已就绪")

    def stop(self):
        """停止协调器"""
        self._running = False
        for agent in self.agents.values():
            agent.stop()
        logger.info("协调器: 已停止")

    def _dispatch_loop(self):
        """任务调度循环"""
        while self._running:
            try:
                # 处理待调度任务
                pending_tasks = [t for t in self.task_queue if t.status == TaskStatus.PENDING]
                for task in pending_tasks:
                    self.dispatch_task(task)
                
                # 处理智能体消息
                for agent in self.agents.values():
                    if agent.message_queue:
                        agent.process_messages()
                
                # 路由待发送消息
                # (实际实现中通过消息总线)
                
                time.sleep(1)  # 调度间隔
            except Exception as e:
                logger.error(f"协调器: 调度循环异常: {e}")

    # ==================== 事件与监控 ====================

    def _on_agent_event(self, event_type: str, data: Any):
        """智能体事件回调"""
        self._emit_event(f'agent_{event_type}', data)

    def add_event_listener(self, listener: Callable):
        """添加事件监听器"""
        self.event_listeners.append(listener)

    def _emit_event(self, event_type: str, data: Any):
        """发射事件"""
        for listener in self.event_listeners:
            try:
                listener(event_type, data)
            except Exception as e:
                logger.error(f"协调器: 事件监听器异常: {e}")

    def get_system_status(self) -> Dict:
        """获取系统全局状态"""
        return {
            'running': self._running,
            'agent_count': len(self.agents),
            'agents': {aid: agent.get_status() for aid, agent in self.agents.items()},
            'task_queue_size': len([t for t in self.task_queue if t.status == TaskStatus.PENDING]),
            'active_workflows': len([w for w in self.workflows.values() if w.status == 'running']),
            'stats': self.stats,
            'uptime': (datetime.now() - datetime.fromisoformat(self.stats['start_time'])).total_seconds()
                      if self.stats['start_time'] else 0
        }

    # ==================== 冲突解决 ====================

    def resolve_conflict(self, agent_id_1: str, agent_id_2: str, conflict_type: str) -> str:
        """
        解决智能体间的冲突
        例如：两个智能体同时想对同一水表执行操作
        """
        logger.warning(f"协调器: 检测到冲突 [{conflict_type}] between [{agent_id_1}] and [{agent_id_2}]")
        
        # 简单策略：优先级高的智能体获胜
        priority_map = {
            'anomaly_agent': 1,      # 异常智能体优先级最高
            'meter_reading_agent': 2,
            'billing_agent': 3,
            'collection_agent': 4,
            'analysis_agent': 5,
        }
        p1 = priority_map.get(agent_id_1, 99)
        p2 = priority_map.get(agent_id_2, 99)
        winner = agent_id_1 if p1 <= p2 else agent_id_2
        logger.info(f"协调器: 冲突解决，胜出智能体 [{winner}]")
        return winner
