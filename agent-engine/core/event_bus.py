"""
事件总线 - 智能体间异步通信的基础设施
支持发布/订阅模式，实现智能体间的松耦合通信
"""
import threading
import time
from typing import Any, Callable, Dict, List, Optional
from collections import defaultdict
from datetime import datetime
from loguru import logger


class Event:
    """事件对象"""
    def __init__(self, event_type: str, data: Any = None, source: str = "",
                 priority: int = 5):
        self.event_id = f"evt_{int(time.time()*1000)}_{id(self)}"
        self.event_type = event_type
        self.data = data
        self.source = source
        self.priority = priority
        self.timestamp = datetime.now()
        self.consumed = False

    def to_dict(self) -> Dict:
        return {
            'event_id': self.event_id,
            'event_type': self.event_type,
            'data': self.data,
            'source': self.source,
            'priority': self.priority,
            'timestamp': self.timestamp.isoformat()
        }


class EventBus:
    """
    事件总线
    
    核心功能:
    1. 事件发布: 智能体发布事件到总线
    2. 事件订阅: 智能体订阅感兴趣的事件类型
    3. 事件过滤: 支持基于类型、来源的过滤
    4. 事件持久化: 关键事件可持久化存储
    5. 死信处理: 未被消费的事件进入死信队列
    """

    def __init__(self, max_queue_size: int = 10000):
        self._lock = threading.Lock()
        self._max_queue_size = max_queue_size
        
        # 订阅关系: event_type -> [handler_list]
        self._subscribers: Dict[str, List[Dict]] = defaultdict(list)
        
        # 事件队列
        self._event_queue: List[Event] = []
        
        # 事件历史（最近的事件）
        self._event_history: List[Event] = []
        self._history_size = 1000
        
        # 死信队列
        self._dead_letter_queue: List[Event] = []
        
        # 统计
        self._stats = {
            'published': 0,
            'consumed': 0,
            'dead_letters': 0,
            'by_type': defaultdict(int)
        }
        
        # 运行控制
        self._running = False
        self._dispatch_thread: Optional[threading.Thread] = None

    def subscribe(self, event_type: str, handler: Callable, 
                  subscriber_id: str = "", filter_func: Callable = None):
        """
        订阅事件
        
        Args:
            event_type: 事件类型，支持通配符 "anomaly.*"
            handler: 事件处理回调函数
            subscriber_id: 订阅者ID
            filter_func: 事件过滤函数
        """
        with self._lock:
            self._subscribers[event_type].append({
                'handler': handler,
                'subscriber_id': subscriber_id,
                'filter': filter_func
            })
        logger.info(f"事件总线: [{subscriber_id}] 订阅事件 [{event_type}]")

    def unsubscribe(self, event_type: str, subscriber_id: str):
        """取消订阅"""
        with self._lock:
            if event_type in self._subscribers:
                self._subscribers[event_type] = [
                    s for s in self._subscribers[event_type]
                    if s['subscriber_id'] != subscriber_id
                ]

    def publish(self, event_type: str, data: Any = None, 
                source: str = "", priority: int = 5):
        """发布事件"""
        event = Event(event_type, data, source, priority)
        
        with self._lock:
            if len(self._event_queue) >= self._max_queue_size:
                logger.warning("事件总线: 队列已满，丢弃最旧事件")
                self._event_queue.pop(0)
            self._event_queue.append(event)
            self._stats['published'] += 1
            self._stats['by_type'][event_type] += 1
        
        logger.debug(f"事件总线: 发布事件 [{event_type}], 来源: [{source}]")

    def start(self):
        """启动事件总线"""
        self._running = True
        self._dispatch_thread = threading.Thread(target=self._dispatch_loop, daemon=True)
        self._dispatch_thread.start()
        logger.info("事件总线: 启动完成")

    def stop(self):
        """停止事件总线"""
        self._running = False
        logger.info("事件总线: 已停止")

    def _dispatch_loop(self):
        """事件分发循环"""
        while self._running:
            try:
                event = self._pop_event()
                if event:
                    self._dispatch_event(event)
                else:
                    time.sleep(0.1)
            except Exception as e:
                logger.error(f"事件总线: 分发异常: {e}")

    def _pop_event(self) -> Optional[Event]:
        """弹出优先级最高的事件"""
        with self._lock:
            if not self._event_queue:
                return None
            # 按优先级排序（数字越小优先级越高）
            self._event_queue.sort(key=lambda e: e.priority)
            event = self._event_queue.pop(0)
            return event

    def _dispatch_event(self, event: Event):
        """分发事件到订阅者"""
        handlers_called = 0
        
        # 精确匹配
        for sub in self._subscribers.get(event.event_type, []):
            if self._should_handle(sub, event):
                try:
                    sub['handler'](event)
                    handlers_called += 1
                except Exception as e:
                    logger.error(f"事件总线: 处理器异常: {e}")
        
        # 通配符匹配 (e.g., "anomaly.*" matches "anomaly.leak")
        for pattern, subs in self._subscribers.items():
            if '*' in pattern and self._match_pattern(pattern, event.event_type):
                for sub in subs:
                    if self._should_handle(sub, event):
                        try:
                            sub['handler'](event)
                            handlers_called += 1
                        except Exception as e:
                            logger.error(f"事件总线: 处理器异常: {e}")
        
        # 记录历史
        with self._lock:
            self._event_history.append(event)
            if len(self._event_history) > self._history_size:
                self._event_history = self._event_history[-self._history_size // 2:]
        
        # 死信处理
        if handlers_called == 0:
            with self._lock:
                self._dead_letter_queue.append(event)
                self._stats['dead_letters'] += 1
            logger.debug(f"事件总线: 事件 [{event.event_type}] 无订阅者，进入死信队列")
        else:
            event.consumed = True
            self._stats['consumed'] += 1

    def _should_handle(self, subscription: Dict, event: Event) -> bool:
        """判断是否应该处理该事件"""
        if subscription.get('filter'):
            try:
                return subscription['filter'](event)
            except:
                return False
        return True

    def _match_pattern(self, pattern: str, event_type: str) -> bool:
        """通配符匹配"""
        prefix = pattern.replace('*', '')
        return event_type.startswith(prefix)

    def get_stats(self) -> Dict:
        """获取统计信息"""
        return {
            'published': self._stats['published'],
            'consumed': self._stats['consumed'],
            'dead_letters': self._stats['dead_letters'],
            'queue_size': len(self._event_queue),
            'subscriber_count': sum(len(v) for v in self._subscribers.values()),
            'by_type': dict(self._stats['by_type'])
        }

    def get_recent_events(self, limit: int = 50) -> List[Dict]:
        """获取最近事件"""
        return [e.to_dict() for e in self._event_history[-limit:]]
