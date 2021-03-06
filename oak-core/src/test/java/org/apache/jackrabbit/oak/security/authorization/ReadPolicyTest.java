/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.security.authorization;

import java.util.Set;
import javax.jcr.security.AccessControlPolicy;

import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.authorization.AbstractAccessControlTest;
import org.apache.jackrabbit.oak.spi.security.authorization.AccessControlConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.AccessControlConstants;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests for the special {@code ReadPolicy} exposed at specified paths.
 */
public class ReadPolicyTest extends AbstractAccessControlTest {

    private String[] readPaths;

    @Override
    @Before
    public void before() throws Exception {
        super.before();

        ConfigurationParameters options = getConfig(AccessControlConfiguration.class).getParameters();
        readPaths = options.getConfigValue(AccessControlConstants.PARAM_READ_PATHS, AccessControlConstants.DEFAULT_READ_PATHS);
    }

    @Test
    public void testGetPolicies() throws Exception {
        for (String path : readPaths) {
            AccessControlPolicy[] policies = getAccessControlManager(root).getPolicies(path);
            assertTrue(policies.length > 0);
            boolean found = false;
            for (AccessControlPolicy policy : policies) {
                if ("org.apache.jackrabbit.oak.security.authorization.AccessControlManagerImpl$ReadPolicy".equals(policy.getClass().getName())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void testGetEffectivePolicies() throws Exception {
        for (String path : readPaths) {
            AccessControlPolicy[] policies = getAccessControlManager(root).getPolicies(path);
            assertTrue(policies.length > 0);
            boolean found = false;
            for (AccessControlPolicy policy : policies) {
                if ("org.apache.jackrabbit.oak.security.authorization.AccessControlManagerImpl$ReadPolicy".equals(policy.getClass().getName())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }
}