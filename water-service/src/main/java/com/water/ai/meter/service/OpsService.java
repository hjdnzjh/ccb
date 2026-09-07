package com.water.ai.meter.service;

import java.util.List;
import java.util.Map;

public interface OpsService {
    Map<String, Object> cockpit();
    List<Map<String, Object>> twinZones();
    Map<String, Object> zoneDetail(String areaCode);
    Map<String, Object> twinMap();
    List<Map<String, Object>> meterHealth();
    List<Map<String, Object>> ocrTrust(int limit);
    List<Map<String, Object>> billingInsights();
    Map<String, Object> assistant(String question);
    List<Map<String, Object>> recentReadings(int limit);
}
