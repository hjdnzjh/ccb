"""
Flask API 路由
提供智能体系统的HTTP接口
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
from datetime import datetime
import logging

# 配置日志
logging.basicConfig(level=logging.INFO)


def create_app(orchestrator=None):
    """创建Flask应用"""
    app = Flask(__name__)
    CORS(app)
    
    # 存储协调器引用
    app.orchestrator = orchestrator
    
    @app.route('/api/health', methods=['GET'])
    def health_check():
        """健康检查"""
        return jsonify({
            'status': 'healthy',
            'timestamp': datetime.now().isoformat(),
            'service': 'water-meter-agent-engine'
        })
    
    @app.route('/api/status', methods=['GET'])
    def system_status():
        """获取系统状态"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        status = app.orchestrator.get_system_status()
        return jsonify({
            'success': True,
            'data': status,
            'timestamp': datetime.now().isoformat()
        })
    
    @app.route('/api/agents', methods=['GET'])
    def list_agents():
        """列出所有智能体"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agents = []
        for agent_id, agent in app.orchestrator.agents.items():
            agents.append(agent.get_status())
        
        return jsonify({
            'success': True,
            'data': agents,
            'count': len(agents)
        })
    
    @app.route('/api/agents/<agent_id>', methods=['GET'])
    def get_agent(agent_id):
        """获取单个智能体状态"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent(agent_id)
        if not agent:
            return jsonify({'error': f'Agent {agent_id} not found'}), 404
        
        return jsonify({
            'success': True,
            'data': agent.get_status()
        })
    
    @app.route('/api/agents/<agent_id>/execute', methods=['POST'])
    def execute_agent(agent_id):
        """执行智能体周期"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent(agent_id)
        if not agent:
            return jsonify({'error': f'Agent {agent_id} not found'}), 404
        
        context = request.json or {}
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': {
                    'message': result.message,
                    'data': result.data,
                    'next_actions': result.next_actions
                },
                'timestamp': datetime.now().isoformat()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    # ==================== 抄表接口 ====================
    
    @app.route('/api/meter-reading', methods=['POST'])
    def meter_reading():
        """抄表接口"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('meter_reading_agent')
        if not agent:
            return jsonify({'error': 'Meter reading agent not found'}), 404
        
        data = request.json or {}
        mode = data.get('mode', 'remote')
        
        context = {
            'mode': mode,
            'meter_id': data.get('meter_id'),
            'district': data.get('district') or data.get('area_id'),
            'area_id': data.get('area_id') or data.get('district'),
            'meter_count': data.get('meter_count', 10),
            'image_data': data.get('image_data'),
            'reading': data.get('reading'),
            'signal_strength': data.get('signal_strength'),
            'protocol': data.get('protocol', 'NB-IoT')
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message,
                'timestamp': datetime.now().isoformat()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/meter-reading/batch', methods=['POST'])
    def batch_meter_reading():
        """批量抄表"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('meter_reading_agent')
        if not agent:
            return jsonify({'error': 'Meter reading agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'schedule',
            'district': data.get('district') or data.get('area_id', 'all'),
            'area_id': data.get('area_id') or data.get('district', 'all'),
            'meter_count': data.get('meter_count', 100)
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message,
                'timestamp': datetime.now().isoformat()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/meter-reading/stats', methods=['GET'])
    def meter_reading_stats():
        """抄表统计"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('meter_reading_agent')
        if not agent:
            return jsonify({'error': 'Meter reading agent not found'}), 404
        
        return jsonify({
            'success': True,
            'data': agent.get_reading_stats()
        })
    
    # ==================== 计费接口 ====================
    
    @app.route('/api/billing', methods=['POST'])
    def create_bill():
        """生成账单"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('billing_agent')
        if not agent:
            return jsonify({'error': 'Billing agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'single',
            'meter_id': data.get('meter_id', 'unknown'),
            'reading': data.get('reading', 0),
            'last_reading': data.get('last_reading', 0),
            'user_type': data.get('user_type', 'residential'),
            'billing_period': data.get('billing_period', datetime.now().strftime('%Y-%m'))
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message,
                'timestamp': datetime.now().isoformat()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/billing/batch', methods=['POST'])
    def batch_billing():
        """批量计费"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('billing_agent')
        if not agent:
            return jsonify({'error': 'Billing agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'batch',
            'readings': data.get('readings', []),
            'billing_period': data.get('billing_period', datetime.now().strftime('%Y-%m'))
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/billing/stats', methods=['GET'])
    def billing_stats():
        """计费统计"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('billing_agent')
        if not agent:
            return jsonify({'error': 'Billing agent not found'}), 404
        
        return jsonify({
            'success': True,
            'data': agent.get_billing_stats()
        })
    
    # ==================== 异常检测接口 ====================
    
    @app.route('/api/anomaly/detect', methods=['POST'])
    def detect_anomaly():
        """异常检测"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('anomaly_agent')
        if not agent:
            return jsonify({'error': 'Anomaly agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'realtime',
            'meter_id': data.get('meter_id', 'unknown'),
            'current_usage': data.get('current_usage', 0),
            'usage_history': data.get('usage_history', []),
            'zero_usage_days': data.get('zero_usage_days', 0)
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message,
                'timestamp': datetime.now().isoformat()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/anomaly/stats', methods=['GET'])
    def anomaly_stats():
        """异常统计"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('anomaly_agent')
        if not agent:
            return jsonify({'error': 'Anomaly agent not found'}), 404
        
        return jsonify({
            'success': True,
            'data': agent.get_anomaly_stats()
        })
    
    # ==================== 催缴接口 ====================
    
    @app.route('/api/collection/execute', methods=['POST'])
    def execute_collection():
        """执行催缴"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('collection_agent')
        if not agent:
            return jsonify({'error': 'Collection agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'single_user',
            'user_id': data.get('user_id', 'unknown'),
            'bill_id': data.get('bill_id', ''),
            'overdue_amount': data.get('overdue_amount', 0),
            'overdue_days': data.get('overdue_days', 0)
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/collection/stats', methods=['GET'])
    def collection_stats():
        """催缴统计"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('collection_agent')
        if not agent:
            return jsonify({'error': 'Collection agent not found'}), 404
        
        return jsonify({
            'success': True,
            'data': agent.get_collection_stats()
        })
    
    # ==================== 分析接口 ====================
    
    @app.route('/api/analysis/meter/<meter_id>', methods=['GET', 'POST'])
    def analyze_meter(meter_id):
        """分析水表"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('analysis_agent')
        if not agent:
            return jsonify({'error': 'Analysis agent not found'}), 404
        
        if request.method == 'POST':
            data = request.json or {}
        else:
            data = {}
        
        context = {
            'mode': 'single_meter',
            'meter_id': meter_id,
            'usage_history': data.get('usage_history'),
            'user_type': data.get('user_type', 'residential')
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/analysis/forecast', methods=['POST'])
    def forecast_usage():
        """预测用水量"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('analysis_agent')
        if not agent:
            return jsonify({'error': 'Analysis agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'forecast',
            'usage_history': data.get('usage_history', []),
            'forecast_periods': data.get('forecast_periods', 30)
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/analysis/report', methods=['POST'])
    def generate_report():
        """生成报告"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        agent = app.orchestrator.get_agent('analysis_agent')
        if not agent:
            return jsonify({'error': 'Analysis agent not found'}), 404
        
        data = request.json or {}
        
        context = {
            'mode': 'report',
            'report_type': data.get('report_type', 'monthly'),
            'period': data.get('period', datetime.now().strftime('%Y-%m'))
        }
        
        try:
            result = agent.run_cycle(context)
            return jsonify({
                'success': result.success,
                'data': result.data,
                'message': result.message
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    # ==================== 工作流接口 ====================
    
    @app.route('/api/workflow/create', methods=['POST'])
    def create_workflow():
        """创建工作流"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        data = request.json or {}
        template_name = data.get('template', 'reading_billing_workflow')
        context = data.get('context', {})
        
        try:
            workflow = app.orchestrator.create_workflow(template_name, context)
            return jsonify({
                'success': True,
                'data': workflow.to_dict(),
                'message': f'Workflow {workflow.workflow_id} created'
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    @app.route('/api/workflow/<workflow_id>/execute', methods=['POST'])
    def execute_workflow(workflow_id):
        """执行工作流"""
        if not app.orchestrator:
            return jsonify({'error': 'Orchestrator not initialized'}), 500
        
        workflow = app.orchestrator.workflows.get(workflow_id)
        if not workflow:
            return jsonify({'error': f'Workflow {workflow_id} not found'}), 404
        
        try:
            results = app.orchestrator.execute_workflow(workflow)
            return jsonify({
                'success': True,
                'data': results,
                'workflow': workflow.to_dict()
            })
        except Exception as e:
            return jsonify({
                'success': False,
                'error': str(e)
            }), 500
    
    return app