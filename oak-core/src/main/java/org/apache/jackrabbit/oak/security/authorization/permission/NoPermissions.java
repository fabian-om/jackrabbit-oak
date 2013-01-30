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
package org.apache.jackrabbit.oak.security.authorization.permission;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;

/**
 * NoPermissions... TODO
 */
public final class NoPermissions implements CompiledPermissions {

    private static final CompiledPermissions INSTANCE = new NoPermissions();

    private NoPermissions() {
    }

    public static final CompiledPermissions getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canRead(@Nonnull Tree tree) {
        return false;
    }

    @Override
    public boolean canRead(@Nonnull Tree tree, @Nonnull PropertyState property) {
        return false;
    }

    @Override
    public boolean isGranted(long permissions) {
        return false;
    }

    @Override
    public boolean isGranted(long permissions, @Nonnull Tree tree) {
        return false;
    }

    @Override
    public boolean isGranted(long permissions, @Nonnull Tree parent, @Nonnull PropertyState property) {
        return false;
    }
}