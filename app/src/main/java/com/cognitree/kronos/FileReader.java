/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cognitree.kronos;

import com.cognitree.kronos.model.definitions.TaskDefinition;
import com.cognitree.kronos.model.definitions.WorkflowDefinition;
import com.cognitree.kronos.scheduler.WorkflowSchedulerService;
import com.cognitree.kronos.scheduler.store.TaskDefinitionStoreService;
import com.cognitree.kronos.scheduler.store.WorkflowDefinitionStoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FileReader {

    private static final Logger logger = LoggerFactory.getLogger(FileReader.class);

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    private static final String DEFAULT_NAMESPACE = "default";
    private static final TypeReference<List<TaskDefinition>> TASK_DEFINITION_LIST_REF =
            new TypeReference<List<TaskDefinition>>() {
            };
    private static final TypeReference<List<WorkflowDefinition>> WORKFLOW_DEFINITION_LIST_REF =
            new TypeReference<List<WorkflowDefinition>>() {
            };

    public void loadTaskDefinitions() throws IOException {
        final InputStream resourceAsStream =
                FileReader.class.getClassLoader().getResourceAsStream("task-definitions.yaml");
        List<TaskDefinition> taskDefinitions = MAPPER.readValue(resourceAsStream, TASK_DEFINITION_LIST_REF);

        for (TaskDefinition taskDefinition : taskDefinitions) {
            if (TaskDefinitionStoreService.getService().load(taskDefinition) == null) {
                TaskDefinitionStoreService.getService().store(taskDefinition);
            } else {
                logger.warn("Task definition with id {} already exists", taskDefinition.getIdentity());
            }
        }
    }

    public void loadWorkflowDefinitions() throws IOException {
        final InputStream resourceAsStream =
                FileReader.class.getClassLoader().getResourceAsStream("workflow-definitions.yaml");
        List<WorkflowDefinition> workflowDefinitions = MAPPER.readValue(resourceAsStream, WORKFLOW_DEFINITION_LIST_REF);

        for (WorkflowDefinition workflowDefinition : workflowDefinitions) {
            if (workflowDefinition.getNamespace() == null) {
                workflowDefinition.setNamespace(DEFAULT_NAMESPACE);
            }
            if (WorkflowDefinitionStoreService.getService().load(workflowDefinition) == null) {
                try {
                    WorkflowSchedulerService.getService().add(workflowDefinition);
                    WorkflowDefinitionStoreService.getService().store(workflowDefinition);
                } catch (Exception ex) {
                    logger.error("Unable to add workflow definition {}", workflowDefinition, ex);
                }
            } else {
                logger.error("Workflow definition already exists with id {}", workflowDefinition.getIdentity());
            }
        }
    }

}