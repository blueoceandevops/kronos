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

import com.cognitree.kronos.scheduler.model.Job;
import com.cognitree.kronos.scheduler.model.JobId;

import static com.cognitree.kronos.scheduler.model.Job.Status;

/**
 * An interface implemented by services interested in {@link Job} status change.
 * To receive notification on {@link Job} status change register the listener with
 * {@link JobService#registerListener(JobStatusChangeListener)}
 */
public interface JobStatusChangeListener {

    void statusChanged(JobId jobId, Status from, Status to);
}
