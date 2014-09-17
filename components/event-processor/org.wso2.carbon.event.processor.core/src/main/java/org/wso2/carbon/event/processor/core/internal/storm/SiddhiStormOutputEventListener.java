/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.event.processor.core.internal.storm;

import org.apache.log4j.Logger;
import org.wso2.carbon.databridge.commons.thrift.utils.HostAddressFinder;
import org.wso2.carbon.event.processor.core.ExecutionPlanConfiguration;
import org.wso2.carbon.event.processor.core.internal.listener.SiddhiOutputStreamListener;
import org.wso2.carbon.event.processor.storm.common.transport.server.TCPEventServer;
import org.wso2.carbon.event.processor.storm.common.transport.server.TCPEventServerConfig;
import org.wso2.carbon.event.processor.storm.common.transport.server.StreamCallback;
import org.wso2.carbon.event.processor.storm.common.helper.StormDeploymentConfiguration;
import org.wso2.carbon.event.processor.storm.common.management.client.ManagerServiceClient;
import org.wso2.carbon.event.processor.storm.common.util.StormUtils;
import org.wso2.siddhi.query.api.definition.StreamDefinition;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Receives events from the Event publisher bolt running on storm. There will be one SiddhiStormOutputEventListener instance
 * per execution plan per tenant (all exported streams of execution plan are handled form a single instance). When events are
 * received from storm, the event  will be directed to the relevant output stream listener depending on the stream to forward
 * the event to the relevant output adaptor for the stream.
 */
public class SiddhiStormOutputEventListener implements StreamCallback {
    private static Logger log = Logger.getLogger(SiddhiStormOutputEventListener.class);
    private ExecutionPlanConfiguration executionPlanConfiguration;
    private int listeningPort;
    private int tenantId;
    private String thisHostIp;
    private int cepMangerPort = StormDeploymentConfiguration.getCepManagerPort();
    private String cepMangerHost = StormDeploymentConfiguration.getCepManagerHost();
    private HashMap<String, SiddhiOutputStreamListener> streamNameToOutputStreamListenerMap = new HashMap<String, SiddhiOutputStreamListener>();
    private int minListeningPort = StormDeploymentConfiguration.getMinListingPort();
    private int maxListeningPort = StormDeploymentConfiguration.getMaxListeningPort();
    private TCPEventServer TCPEventServer;

    public SiddhiStormOutputEventListener(ExecutionPlanConfiguration executionPlanConfiguration, int tenantId) {
        this.executionPlanConfiguration = executionPlanConfiguration;
        this.tenantId = tenantId;
        init();
    }

    public void registerOutputStreamListener(StreamDefinition siddhiStreamDefinition, SiddhiOutputStreamListener outputStreamListener) {
        log.info("Registering output stream listener for Siddhi stream : " + siddhiStreamDefinition.getStreamId());
        streamNameToOutputStreamListenerMap.put(siddhiStreamDefinition.getStreamId(), outputStreamListener);
        TCPEventServer.subscribe(siddhiStreamDefinition);
    }

    private void registerWithCepMangerService() {
        log.info("Registering CEP publisher for " + executionPlanConfiguration.getName() + ":" + tenantId + " at " + thisHostIp + ":" + listeningPort);
        ManagerServiceClient client = new ManagerServiceClient(cepMangerHost, cepMangerPort, null);
        client.registerCepPublisher(executionPlanConfiguration.getName(), tenantId, thisHostIp, listeningPort, StormDeploymentConfiguration.getReconnectInterval());
    }

    private void init() {
        log.info("[CEP Publisher] Initializing storm output event listener for execution plan '" + executionPlanConfiguration.getName() + "'"
                + "(TenantID=" + tenantId + ")");

        try {
            selectPort();
            thisHostIp = HostAddressFinder.findAddress("localhost");
            TCPEventServer = new TCPEventServer(new TCPEventServerConfig(listeningPort), this);
            TCPEventServer.start();
            registerWithCepMangerService();
        } catch (Exception e) {
            log.error("Failed to start event listener for execution plan :" + executionPlanConfiguration.getName(), e);
        }
    }

    private void selectPort() {
        for (int i = minListeningPort; i <= maxListeningPort; i++) {
            if (!StormUtils.isPortUsed(i)) {
                listeningPort = i;
                break;
            }
        }
    }


    @Override
    public void receive(String streamId, Object[] event) {
        SiddhiOutputStreamListener outputStreamListener = streamNameToOutputStreamListenerMap.get(streamId);
        if (outputStreamListener != null) {
            outputStreamListener.sendEventData(event);
        } else {
            log.warn("Cannot find output event listener for stream " + streamId + " in execution plan " + executionPlanConfiguration.getName()
                    + " of tenant " + tenantId + ". Discarding event:" + Arrays.deepToString(event));
        }
    }
}