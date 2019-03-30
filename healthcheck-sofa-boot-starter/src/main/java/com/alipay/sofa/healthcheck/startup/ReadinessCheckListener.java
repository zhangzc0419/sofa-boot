/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.healthcheck.startup;

import com.alipay.sofa.healthcheck.configuration.HealthCheckConstants;
import com.alipay.sofa.healthcheck.core.*;
import com.alipay.sofa.healthcheck.log.SofaBootHealthCheckLoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check start checker.
 * @author liangen
 * @author qilong.zql
 */
public class ReadinessCheckListener implements ApplicationContextAware, PriorityOrdered,
                                   ApplicationListener<ContextRefreshedEvent> {

    private static Logger                        logger                 = SofaBootHealthCheckLoggerFactory
                                                                            .getLogger(ReadinessCheckListener.class);
    private ApplicationContext                   applicationContext;

    @Autowired
    private Environment                          environment;

    @Autowired
    private HealthCheckerProcessor               healthCheckerProcessor;

    @Autowired
    private HealthIndicatorProcessor             healthIndicatorProcessor;

    @Autowired
    private AfterReadinessCheckCallbackProcessor afterReadinessCheckCallbackProcessor;

    private boolean                              healthCheckerStatus    = true;

    private Map<String, Health>                  healthCheckerDetails   = new HashMap<>();

    private boolean                              healthIndicatorStatus  = true;

    private Map<String, Health>                  healthIndicatorDetails = new HashMap<>();

    private boolean                              healthCallbackStatus   = true;
    private boolean                              readinessCheckFinish   = false;

    @Override
    public void setApplicationContext(ApplicationContext cxt) throws BeansException {
        applicationContext = cxt;
    }

    private Map<String, Health> healthCallbackDetails = new HashMap<>();

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 10;
    }

    @Override
    /**
     * 初始化完毕事件做readiness check
     */
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (applicationContext.equals(event.getApplicationContext())) {
            healthCheckerProcessor.init();
            healthIndicatorProcessor.init();
            afterReadinessCheckCallbackProcessor.init();
            readinessHealthCheck();
            readinessCheckFinish = true;
        }
    }

    /**
     * Do readiness health check.
     */
    public void readinessHealthCheck() {
        if (skipAllCheck()) {
            logger.warn("Skip all readiness health check.");
        } else {
            if (skipComponent()) {
                logger.warn("Skip HealthChecker health check.");
            } else {
                //中间件readinessHealthCheck
                healthCheckerStatus = healthCheckerProcessor
                    .readinessHealthCheck(healthCheckerDetails);
            }
            if (skipIndicator()) {
                logger.warn("Skip HealthIndicator health check.");
            } else {
                //健康指标readinessHealthCheck
                healthIndicatorStatus = healthIndicatorProcessor
                    .readinessHealthCheck(healthIndicatorDetails);
            }
        }
        //回调事件
        healthCallbackStatus = afterReadinessCheckCallbackProcessor
            .afterReadinessCheckCallback(healthCallbackDetails);
        if (healthCheckerStatus && healthIndicatorStatus && healthCallbackStatus) {
            logger.info("Readiness check result: success");
        } else {
            logger.error("Readiness check result: fail");
        }
    }

    public boolean skipAllCheck() {
        String skipAllCheck = environment
            .getProperty(HealthCheckConstants.SOFABOOT_SKIP_ALL_HEALTH_CHECK);
        return StringUtils.hasText(skipAllCheck) && "true".equalsIgnoreCase(skipAllCheck);
    }

    public boolean skipComponent() {
        String skipComponent = environment
            .getProperty(HealthCheckConstants.SOFABOOT_SKIP_COMPONENT_HEALTH_CHECK);
        return StringUtils.hasText(skipComponent) && "true".equalsIgnoreCase(skipComponent);
    }

    public boolean skipIndicator() {
        String skipIndicator = environment
            .getProperty(HealthCheckConstants.SOFABOOT_SKIP_HEALTH_INDICATOR_CHECK);
        return StringUtils.hasText(skipIndicator) && "true".equalsIgnoreCase(skipIndicator);
    }

    public boolean getHealthCheckerStatus() {
        return healthCheckerStatus;
    }

    public Map<String, Health> getHealthCheckerDetails() {
        return healthCheckerDetails;
    }

    public boolean getHealthIndicatorStatus() {
        return healthIndicatorStatus;
    }

    public Map<String, Health> getHealthIndicatorDetails() {
        return healthIndicatorDetails;
    }

    public boolean getHealthCallbackStatus() {
        return healthCallbackStatus;
    }

    public Map<String, Health> getHealthCallbackDetails() {
        return healthCallbackDetails;
    }

    public boolean isReadinessCheckFinish() {
        return readinessCheckFinish;
    }
}
