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

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.jcr.security.AccessControlManager;

import com.google.common.collect.ImmutableList;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.namepath.NamePathMapper;
import org.apache.jackrabbit.oak.plugins.version.VersionablePathHook;
import org.apache.jackrabbit.oak.security.authorization.permission.PermissionHook;
import org.apache.jackrabbit.oak.security.authorization.permission.PermissionProviderImpl;
import org.apache.jackrabbit.oak.security.authorization.permission.PermissionStoreValidatorProvider;
import org.apache.jackrabbit.oak.security.authorization.permission.PermissionValidatorProvider;
import org.apache.jackrabbit.oak.security.authorization.restriction.RestrictionProviderImpl;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.ValidatorProvider;
import org.apache.jackrabbit.oak.spi.lifecycle.WorkspaceInitializer;
import org.apache.jackrabbit.oak.spi.security.ConfigurationBase;
import org.apache.jackrabbit.oak.spi.security.Context;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AccessControlConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionProvider;
import org.apache.jackrabbit.oak.spi.xml.ProtectedItemImporter;

/**
 * Default implementation of the {@code AccessControlConfiguration}.
 */
public class AccessControlConfigurationImpl extends ConfigurationBase implements AccessControlConfiguration {

    public AccessControlConfigurationImpl(SecurityProvider securityProvider) {
        super(securityProvider);
    }

    //----------------------------------------------< SecurityConfiguration >---
    @Nonnull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Context getContext() {
        return AccessControlContext.getInstance();
    }

    @Nonnull
    @Override
    public WorkspaceInitializer getWorkspaceInitializer() {
        return new AccessControlInitializer();
    }

    @Nonnull
    @Override
    public List<? extends CommitHook> getCommitHooks(String workspaceName) {
        return ImmutableList.of(
                new VersionablePathHook(workspaceName),
                new PermissionHook(workspaceName, getRestrictionProvider()));
    }

    @Override
    public List<ValidatorProvider> getValidators(String workspaceName) {
        return ImmutableList.of(
                new PermissionStoreValidatorProvider(),
                new PermissionValidatorProvider(getSecurityProvider()),
                new AccessControlValidatorProvider(getSecurityProvider()));
    }

    @Nonnull
    @Override
    public List<ProtectedItemImporter> getProtectedItemImporters() {
        return Collections.<ProtectedItemImporter>singletonList(new AccessControlImporter(getSecurityProvider()));
    }

    //-----------------------------------------< AccessControlConfiguration >---
    @Override
    public AccessControlManager getAccessControlManager(Root root, NamePathMapper namePathMapper) {
        return new AccessControlManagerImpl(root, namePathMapper, getSecurityProvider());
    }

    @Nonnull
    @Override
    public RestrictionProvider getRestrictionProvider() {
        return new RestrictionProviderImpl();
    }

    @Nonnull
    @Override
    public PermissionProvider getPermissionProvider(Root root, Set<Principal> principals) {
        return new PermissionProviderImpl(root, principals, getSecurityProvider());
    }
}
