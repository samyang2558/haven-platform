/*
 * Copyright 2016 Code Above Lab LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codeabovelab.dm.cluman.batch;

import com.codeabovelab.dm.cluman.job.JobComponent;
import com.codeabovelab.dm.cluman.job.JobContext;
import com.codeabovelab.dm.cluman.job.JobParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 */
@JobComponent
class StopThenStartEachStrategy {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    private LoadContainersOfImageTasklet loader;

    @Autowired
    private StopContainerTasklet containerStopper;

    @Autowired
    private RemoveContainerTasklet containerRemover;

    @Autowired
    private UpgradeImageVersionTasklet imageUpgrader;

    @Autowired
    private CreateContainerTasklet containerCreator;

    @Autowired
    private HealthCheckContainerTasklet healthchecker;

    @Autowired
    private RollbackTasklet rollbacker;

    @Autowired
    private JobContext jobContext;

    @Autowired
    private ContainerConfigTasklet containerConfig;

    @JobParam(BatchUtils.JP_ROLLBACK_ENABLE)
    private boolean rollbackEnable;

    /**
     *
     * @param predicate filter containers
     * @param processor change containers
     */
    public void run(ContainerPredicate predicate, ContainerProcessor processor) {
        List<ProcessedContainer> containers = loader.getContainers(predicate);
        updateContainer(containers, processor);
    }

    protected void updateContainer(List<ProcessedContainer> containers, ContainerProcessor processor) {
        boolean needRollback = this.rollbackEnable;
        ProcessedContainer curr = null;
        try {
            for(ProcessedContainer container: containers) {
                ProcessedContainer withConfig = containerConfig.process(container);
                containerStopper.execute(container);
                containerRemover.execute(container);
                ProcessedContainer newVersion = processor.apply(withConfig);
                ProcessedContainer newContainer = containerCreator.execute(newVersion);
                if(!healthchecker.execute(newContainer) && (needRollback = this.rollbackEnable)) {
                    break;
                }
            }
        } catch (Exception e) {
            needRollback = this.rollbackEnable;
            if(needRollback) {
                jobContext.fire("Error on container {0}, try rollback", curr);
                LOG.error("Error on container {}, try rollback", curr, e);
            } else {
                jobContext.fire("Error on container {0}", curr);
                throw e;
            }
        }
        if(needRollback) {
            rollbacker.rollback();
        }
    }
}
