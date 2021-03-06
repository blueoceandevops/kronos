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

package com.cognitree.kronos.scheduler;

import com.cognitree.kronos.Service;
import com.cognitree.kronos.ServiceException;
import com.cognitree.kronos.ServiceProvider;
import com.cognitree.kronos.scheduler.model.Namespace;
import com.cognitree.kronos.scheduler.model.NamespaceId;
import com.cognitree.kronos.scheduler.model.WorkflowId;
import com.cognitree.kronos.scheduler.model.WorkflowTrigger;
import com.cognitree.kronos.scheduler.model.WorkflowTriggerId;
import com.cognitree.kronos.scheduler.store.StoreException;
import com.cognitree.kronos.scheduler.store.StoreService;
import com.cognitree.kronos.scheduler.store.WorkflowTriggerStore;
import com.cognitree.kronos.scheduler.util.TriggerHelper;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.cognitree.kronos.scheduler.ValidationError.INVALID_WORKFLOW_TRIGGER;
import static com.cognitree.kronos.scheduler.ValidationError.NAMESPACE_NOT_FOUND;
import static com.cognitree.kronos.scheduler.ValidationError.WORKFLOW_NOT_FOUND;
import static com.cognitree.kronos.scheduler.ValidationError.WORKFLOW_TRIGGER_ALREADY_EXISTS;
import static com.cognitree.kronos.scheduler.ValidationError.WORKFLOW_TRIGGER_NOT_FOUND;

public class WorkflowTriggerService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowSchedulerService.class);

    private WorkflowTriggerStore workflowTriggerStore;

    public static WorkflowTriggerService getService() {
        return (WorkflowTriggerService) ServiceProvider.getService(WorkflowTriggerService.class.getSimpleName());
    }

    @Override
    public void init() {
        logger.info("Initializing workflow trigger service");
    }

    @Override
    public void start() {
        logger.info("Starting workflow trigger service");
        StoreService storeService = (StoreService) ServiceProvider.getService(StoreService.class.getSimpleName());
        workflowTriggerStore = storeService.getWorkflowTriggerStore();
        ServiceProvider.registerService(this);
    }

    public void add(WorkflowTrigger workflowTrigger) throws SchedulerException, ServiceException, ValidationException {
        logger.info("Received request to add workflow trigger {}", workflowTrigger);
        validateWorkflow(workflowTrigger.getNamespace(), workflowTrigger.getWorkflow());
        validateTrigger(workflowTrigger);
        try {
            if (workflowTriggerStore.load(workflowTrigger) != null) {
                throw WORKFLOW_TRIGGER_ALREADY_EXISTS.createException(workflowTrigger.getName(),
                        workflowTrigger.getWorkflow(), workflowTrigger.getNamespace());
            }
            WorkflowSchedulerService.getService().add(workflowTrigger);
            workflowTriggerStore.store(workflowTrigger);
        } catch (StoreException | ParseException e) {
            logger.error("unable to add workflow trigger {}", workflowTrigger, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public List<WorkflowTrigger> resume(WorkflowId workflowId)
            throws SchedulerException, ServiceException, ValidationException {
        logger.info("Received request to resume all workflow trigger for workflow {}", workflowId);
        final ArrayList<WorkflowTrigger> affectedTriggers = new ArrayList<>();
        final List<WorkflowTrigger> workflowTriggers = get(workflowId.getNamespace(), workflowId.getName());
        for (WorkflowTrigger workflowTrigger : workflowTriggers) {
            final WorkflowTrigger affectedTrigger = resume(workflowTrigger);
            if (affectedTrigger != null) {
                affectedTriggers.add(affectedTrigger);
            }
        }
        return affectedTriggers;
    }

    public WorkflowTrigger resume(WorkflowTriggerId workflowTriggerId) throws SchedulerException, ServiceException, ValidationException {
        logger.info("Received request to resume workflow trigger {}", workflowTriggerId);
        try {
            final WorkflowTrigger workflowTrigger = workflowTriggerStore.load(workflowTriggerId);
            if (workflowTrigger == null) {
                throw WORKFLOW_TRIGGER_NOT_FOUND.createException(workflowTriggerId.getName(),
                        workflowTriggerId.getWorkflow(), workflowTriggerId.getNamespace());
            }
            if (workflowTrigger.isEnabled()) {
                return null;
            }
            workflowTrigger.setEnabled(true);
            WorkflowSchedulerService.getService().resume(workflowTrigger);
            workflowTriggerStore.update(workflowTrigger);
            return workflowTrigger;
        } catch (StoreException e) {
            logger.error("unable to resume workflow trigger {}", workflowTriggerId, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public List<WorkflowTrigger> pause(WorkflowId workflowId) throws ServiceException, ValidationException {
        logger.info("Received request to pause all triggers for workflow {}", workflowId);
        final ArrayList<WorkflowTrigger> affectedTriggers = new ArrayList<>();
        final List<WorkflowTrigger> workflowTriggers = get(workflowId.getNamespace(), workflowId.getName());
        for (WorkflowTrigger workflowTrigger : workflowTriggers) {
            final WorkflowTrigger affectedTrigger = pause(workflowTrigger);
            if (affectedTrigger != null) {
                affectedTriggers.add(affectedTrigger);
            }
        }
        return affectedTriggers;
    }

    public WorkflowTrigger pause(WorkflowTriggerId workflowTriggerId) throws ServiceException, ValidationException {
        logger.info("Received request to pause workflow trigger {}", workflowTriggerId);
        try {
            final WorkflowTrigger workflowTrigger = workflowTriggerStore.load(workflowTriggerId);
            if (workflowTrigger == null) {
                throw WORKFLOW_TRIGGER_NOT_FOUND.createException(workflowTriggerId.getName(),
                        workflowTriggerId.getWorkflow(), workflowTriggerId.getNamespace());
            }
            if (!workflowTrigger.isEnabled()) {
                logger.warn("workflow trigger {} is already in pause state", workflowTrigger);
                return null;
            }
            workflowTrigger.setEnabled(false);
            WorkflowSchedulerService.getService().pause(workflowTrigger);
            workflowTriggerStore.update(workflowTrigger);
            return workflowTrigger;
        } catch (StoreException | SchedulerException e) {
            logger.error("unable to pause workflow trigger {}", workflowTriggerId, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public List<WorkflowTrigger> get(String namespace) throws ServiceException, ValidationException {
        logger.debug("Received request to get all workflow triggers under namespace {}", namespace);
        validateNamespace(namespace);
        try {
            final List<WorkflowTrigger> workflowTriggers = workflowTriggerStore.load(namespace);
            return workflowTriggers == null ? Collections.emptyList() : workflowTriggers;
        } catch (StoreException e) {
            logger.error("unable to get all workflow triggers under namespace {}", namespace, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public WorkflowTrigger get(WorkflowTriggerId workflowTriggerId) throws ServiceException, ValidationException {
        logger.debug("Received request to get workflow trigger with id {}", workflowTriggerId);
        validateWorkflow(workflowTriggerId.getNamespace(), workflowTriggerId.getWorkflow());
        try {
            return workflowTriggerStore.load(workflowTriggerId);
        } catch (StoreException e) {
            logger.error("unable to get workflow trigger with id {}", workflowTriggerId, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public List<WorkflowTrigger> get(String namespace, String workflowName) throws ServiceException, ValidationException {
        logger.debug("Received request to get all workflow triggers for workflow {} under namespace {}",
                workflowName, namespace);
        validateWorkflow(namespace, workflowName);
        try {
            final List<WorkflowTrigger> workflowTriggers = workflowTriggerStore.loadByWorkflowName(namespace, workflowName);
            return workflowTriggers == null ? Collections.emptyList() : workflowTriggers;
        } catch (StoreException e) {
            logger.error("unable to get all workflow triggers for workflow {} under namespace {}",
                    workflowName, namespace, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public List<WorkflowTrigger> get(String namespace, String workflowName, boolean enabled) throws ServiceException, ValidationException {
        logger.debug("Received request to get all enabled {} workflow triggers for workflow {} under namespace {}",
                enabled, workflowName, namespace);
        validateWorkflow(namespace, workflowName);
        try {
            final List<WorkflowTrigger> workflowTriggers =
                    workflowTriggerStore.loadByWorkflowNameAndEnabled(namespace, workflowName, enabled);
            return workflowTriggers == null ? Collections.emptyList() : workflowTriggers;
        } catch (StoreException e) {
            logger.error("unable to get all enabled {} workflow triggers for workflow {} under namespace {}",
                    enabled, workflowName, namespace, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    public void delete(WorkflowTriggerId workflowTriggerId) throws SchedulerException, ServiceException, ValidationException {
        logger.info("Received request to delete workflow trigger {}", workflowTriggerId);
        validateNamespace(workflowTriggerId.getNamespace());
        try {
            if (workflowTriggerStore.load(workflowTriggerId) == null) {
                throw WORKFLOW_TRIGGER_NOT_FOUND.createException(workflowTriggerId.getName(),
                        workflowTriggerId.getWorkflow(), workflowTriggerId.getNamespace());
            }
            WorkflowSchedulerService.getService().delete(workflowTriggerId);
            workflowTriggerStore.delete(workflowTriggerId);
        } catch (StoreException e) {
            logger.error("unable to delete workflow trigger {}", workflowTriggerId, e);
            throw new ServiceException(e.getMessage(), e.getCause());
        }
    }

    private void validateTrigger(WorkflowTrigger workflowTrigger) throws ValidationException {
        if (!workflowTrigger.isEnabled()) {
            throw INVALID_WORKFLOW_TRIGGER.createException("trigger is in disabled mode");
        }
        try {
            TriggerHelper.buildTrigger(workflowTrigger);
        } catch (Exception e) {
            logger.error("Error validating workflow trigger {}", workflowTrigger, e);
            throw INVALID_WORKFLOW_TRIGGER.createException(e.getMessage());
        }
    }

    private void validateWorkflow(String namespace, String workflowName) throws ServiceException, ValidationException {
        WorkflowId workflowId = WorkflowId.build(namespace, workflowName);
        if (WorkflowService.getService().get(workflowId) == null) {
            logger.error("No workflow exists with name {} under namespace {}", workflowName, namespace);
            throw WORKFLOW_NOT_FOUND.createException(workflowName, namespace);
        }
    }

    private void validateNamespace(String name) throws ValidationException, ServiceException {
        final Namespace namespace = NamespaceService.getService().get(NamespaceId.build(name));
        if (namespace == null) {
            throw NAMESPACE_NOT_FOUND.createException(name);
        }
    }

    @Override
    public void stop() {
        logger.info("Stopping workflow trigger service");
    }
}