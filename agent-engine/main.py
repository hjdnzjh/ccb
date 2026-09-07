"""
智能体引擎主程序
启动多智能体系统和API服务
"""
import os
import sys
from loguru import logger
from dotenv import load_dotenv

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core import AgentOrchestrator, KnowledgeBase, EventBus
from agents import (
    MeterReadingAgent,
    BillingAgent,
    AnomalyAgent,
    CollectionAgent,
    AnalysisAgent
)
from api import create_app


def setup_logging():
    """配置日志"""
    logger.remove()
    logger.add(
        sys.stdout,
        format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan> - <level>{message}</level>",
        level="INFO"
    )
    logger.add(
        "logs/agent_engine_{time}.log",
        rotation="1 day",
        retention="7 days",
        level="DEBUG"
    )


def initialize_system():
    """初始化智能体系统"""
    logger.info("=" * 60)
    logger.info("初始化基于AI的水表抄表收费管理智能体系统")
    logger.info("=" * 60)
    
    # 创建共享组件
    knowledge_base = KnowledgeBase()
    event_bus = EventBus()
    
    # 创建协调器
    orchestrator = AgentOrchestrator()
    
    # 创建并注册智能体
    logger.info("注册智能体...")
    
    meter_reading_agent = MeterReadingAgent(knowledge_base)
    orchestrator.register_agent(meter_reading_agent)
    
    billing_agent = BillingAgent(knowledge_base)
    orchestrator.register_agent(billing_agent)
    
    anomaly_agent = AnomalyAgent(knowledge_base)
    orchestrator.register_agent(anomaly_agent)
    
    collection_agent = CollectionAgent(knowledge_base)
    orchestrator.register_agent(collection_agent)
    
    analysis_agent = AnalysisAgent(knowledge_base)
    orchestrator.register_agent(analysis_agent)
    
    logger.info(f"已注册 {len(orchestrator.agents)} 个智能体")
    
    # 启动系统
    logger.info("启动智能体系统...")
    orchestrator.start()
    event_bus.start()
    
    logger.info("系统初始化完成!")
    
    return orchestrator, knowledge_base, event_bus


def run_demo(orchestrator):
    """运行演示"""
    logger.info("\n" + "=" * 60)
    logger.info("开始运行智能体系统演示")
    logger.info("=" * 60)
    
    # 演示1: 抄表智能体（CSV 传感数据）
    logger.info("\n--- 演示1: 抄表智能体 (CSV) ---")
    meter_agent = orchestrator.get_agent('meter_reading_agent')
    
    # 单表远程抄表（从 CSV 取真实样本）
    result1 = meter_agent.run_cycle({
        'mode': 'remote',
        'meter_id': 'HZ000001'
    })
    logger.info(f"CSV传感抄表结果: {result1.message}")
    
    # 按区批量抄表
    result2 = meter_agent.run_cycle({
        'mode': 'schedule',
        'district': '西湖区',
        'meter_count': 5
    })
    logger.info(f"批量抄表结果: {result2.message}")
    
    # 演示异常告警样本（若存在）
    store = getattr(meter_agent, 'meter_store', None)
    alarm_meter = next((r['meter_id'] for r in (store.rows if store else []) if r.get('alarm')), None)
    if alarm_meter:
        result_alarm = meter_agent.run_cycle({'mode': 'remote', 'meter_id': alarm_meter})
        logger.info(f"告警表抄表结果: {result_alarm.message}")
    
    # 演示2: 计费智能体
    logger.info("\n--- 演示2: 计费智能体 ---")
    billing_agent = orchestrator.get_agent('billing_agent')
    
    reading = (result1.data or {}).get('reading', 1256.5) if result1.data else 1256.5
    last_reading = (result1.data or {}).get('last_reading', reading - 18) if result1.data else 1230.0
    result3 = billing_agent.run_cycle({
        'mode': 'single',
        'meter_id': (result1.data or {}).get('meter_id', 'HZ000001') if result1.data else 'HZ000001',
        'reading': reading,
        'last_reading': last_reading,
        'user_type': 'residential'
    })
    logger.info(f"计费结果: {result3.message}")
    
    # 演示3: 异常检测智能体
    logger.info("\n--- 演示3: 异常检测智能体 ---")
    anomaly_agent = orchestrator.get_agent('anomaly_agent')
    
    result4 = anomaly_agent.run_cycle({
        'mode': 'realtime',
        'meter_id': 'WM-A001-0001',
        'current_usage': 150,
        'usage_history': [10, 12, 11, 10, 13, 11, 10, 12, 11, 10],
        'zero_usage_days': 0
    })
    logger.info(f"异常检测结果: {result4.message}")
    
    # 演示4: 催缴智能体
    logger.info("\n--- 演示4: 催缴智能体 ---")
    collection_agent = orchestrator.get_agent('collection_agent')
    
    result5 = collection_agent.run_cycle({
        'mode': 'single_user',
        'user_id': 'U001',
        'bill_id': 'BILL-202401-0001',
        'overdue_amount': 156.80,
        'overdue_days': 15
    })
    logger.info(f"催缴结果: {result5.message}")
    
    # 演示5: 分析智能体
    logger.info("\n--- 演示5: 分析智能体 ---")
    analysis_agent = orchestrator.get_agent('analysis_agent')
    
    result6 = analysis_agent.run_cycle({
        'mode': 'single_meter',
        'meter_id': 'WM-A001-0001',
        'user_type': 'residential'
    })
    logger.info(f"分析结果: {result6.message}")
    
    # 演示6: 工作流执行
    logger.info("\n--- 演示6: 工作流执行 ---")
    workflow = orchestrator.create_workflow('reading_billing_workflow', {
        'meter_id': 'WM-A001-0002'
    })
    workflow_result = orchestrator.execute_workflow(workflow)
    logger.info(f"工作流执行完成: {workflow.status}")
    
    # 系统状态
    logger.info("\n--- 系统状态 ---")
    status = orchestrator.get_system_status()
    logger.info(f"运行时间: {status['uptime']:.1f}秒")
    logger.info(f"任务统计: 创建{status['stats']['tasks_created']}, 完成{status['stats']['tasks_completed']}")
    logger.info(f"消息路由: {status['stats']['messages_routed']}条")
    
    logger.info("\n" + "=" * 60)
    logger.info("演示完成!")
    logger.info("=" * 60)


def main():
    """主函数"""
    # 加载环境变量
    load_dotenv()
    
    # 配置日志
    setup_logging()
    
    # 初始化系统
    orchestrator, knowledge_base, event_bus = initialize_system()
    
    # 运行演示
    run_demo(orchestrator)
    
    # 获取配置
    host = os.getenv('FLASK_HOST', '0.0.0.0')
    port = int(os.getenv('FLASK_PORT', 8087))
    debug = os.getenv('FLASK_DEBUG', 'True').lower() == 'true'
    
    # 创建Flask应用
    app = create_app(orchestrator)
    
    logger.info(f"\n启动API服务: http://{host}:{port}")
    logger.info("API文档:")
    logger.info("  GET  /api/health         - 健康检查")
    logger.info("  GET  /api/status         - 系统状态")
    logger.info("  GET  /api/agents         - 智能体列表")
    logger.info("  POST /api/meter-reading  - 执行抄表")
    logger.info("  POST /api/billing        - 生成账单")
    logger.info("  POST /api/anomaly/detect - 异常检测")
    logger.info("  POST /api/collection/execute - 执行催缴")
    logger.info("  POST /api/analysis/forecast  - 用水预测")
    logger.info("  POST /api/workflow/create    - 创建工作流")
    
    # 启动API服务
    app.run(host=host, port=port, debug=debug, use_reloader=False)


if __name__ == '__main__':
    main()