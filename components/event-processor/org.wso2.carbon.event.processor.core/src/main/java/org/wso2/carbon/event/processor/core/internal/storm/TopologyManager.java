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

import backtype.storm.StormSubmitter;
import backtype.storm.generated.*;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.utils.NimbusClient;
import backtype.storm.utils.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.thrift7.TException;
import org.json.simple.JSONValue;
import org.wso2.carbon.event.processor.core.exception.ExecutionPlanConfigurationException;
import org.wso2.carbon.event.processor.core.exception.StormDeploymentException;
import org.wso2.carbon.event.processor.core.internal.storm.util.StormTopologyConstructor;
import org.wso2.carbon.event.processor.storm.common.config.StormDeploymentConfig;
import org.wso2.carbon.event.processor.storm.common.util.StormConfigReader;

import javax.xml.stream.XMLStreamException;
import java.util.List;
import java.util.Map;

/**
 * The Siddhi Topology Manager
 */
public class TopologyManager {

    private static Map stormConfig;
    private static StormDeploymentConfig stormDeploymentConfig;
    private static Nimbus.Client client;
    private static String jarLocation = "/Users/suho/wso2/src/dev/carbon-event-processing/components/event-processor/org.wso2.carbon.event.processor.storm.topology/target/org.wso2.carbon.event.processor.storm.topology-1.0.0-SNAPSHOT.jar";
    private static final Log log = LogFactory.getLog(TopologyManager.class);


    static {
        System.setProperty("storm.yaml", "/Users/suho/wso2/packs/cep/storm/apache-storm-0.9.2-incubating/conf/storm.yaml");
        stormConfig = Utils.readStormConfig();
        client = NimbusClient.getConfiguredClient(stormConfig).getClient();
        stormDeploymentConfig = StormConfigReader.loadConfigurations("/Users/suho/wso2/src/dev/product-cep/modules/distribution/target/receiver/wso2cep-4.0.0-SNAPSHOT/");
    }

    public static List<TopologySummary> getTopologies() throws StormDeploymentException {

        try {
            return client.getClusterInfo().get_topologies();
        } catch (TException e) {
            throw new StormDeploymentException("Cannot get topologies from strom cluster", e);
        }
    }

    public static void submitTopology(String executionPlanName, String stormQueryPlan, int tenantId) throws StormDeploymentException, ExecutionPlanConfigurationException {

        TopologyBuilder builder = null;
        try {
            builder = StormTopologyConstructor.constructTopologyBuilder(stormQueryPlan, executionPlanName, tenantId, stormDeploymentConfig);
        } catch (XMLStreamException e) {
            throw new ExecutionPlanConfigurationException("Invalid Config for Execution Plan " + executionPlanName + " for tenant " + tenantId, e);
        }

        String uploadedJarLocation = StormSubmitter.submitJar(stormConfig, jarLocation);

        try {
            String jsonConf = JSONValue.toJSONString(stormConfig);
            client.submitTopology(executionPlanName,
                    uploadedJarLocation, jsonConf, builder.createTopology());
            log.info("Successfully submitted storm topology " + executionPlanName);
        } catch (AlreadyAliveException e) {
            throw new ExecutionPlanConfigurationException("Topology already exist with name " + executionPlanName, e);
        } catch (TException e) {
            throw new StormDeploymentException("Error connecting to Storm", e);
        } catch (InvalidTopologyException e) {
            throw new ExecutionPlanConfigurationException("Invalid Execution Plan " + executionPlanName + " for tenant " + tenantId, e);
        }
    }

    public static void killTopology(String executionPlanName, int tenantId) throws StormDeploymentException, ExecutionPlanConfigurationException {

        try {
            client.killTopologyWithOpts(executionPlanName, new KillOptions()); //provide topology name
        } catch (NotAliveException e) {
            // do nothing
        } catch (TException e) {
            throw new StormDeploymentException("Error connecting to Storm", e);
        }
    }

}
