/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.spi.lifecycle;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.plugins.index.IndexUpdateProvider;
import org.apache.jackrabbit.oak.plugins.index.IndexEditorProvider;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.EditorHook;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.state.NodeStoreBranch;

public final class OakInitializer {

    private OakInitializer() {
    }

    public static void initialize(@Nonnull NodeStore store,
                                  @Nonnull RepositoryInitializer initializer,
                                  @Nonnull IndexEditorProvider indexEditor) {
        NodeStoreBranch branch = store.branch();
        NodeState before = branch.getHead();
        branch.setRoot(initializer.initialize(before));
        try {
            branch.merge(new EditorHook(new IndexUpdateProvider(indexEditor)));
        } catch (CommitFailedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initialize(@Nonnull Iterable<WorkspaceInitializer> initializer,
                                  @Nonnull NodeStore store,
                                  @Nonnull String workspaceName,
                                  @Nonnull IndexEditorProvider indexEditor,
                                  @Nonnull QueryIndexProvider indexProvider,
                                  @Nonnull CommitHook commitHook) {
        NodeStoreBranch branch = store.branch();
        NodeState root = branch.getHead();
        for (WorkspaceInitializer wspInit : initializer) {
            root = wspInit.initialize(root, workspaceName, indexProvider, commitHook);
        }
        branch.setRoot(root);
        try {
            branch.merge(new EditorHook(new IndexUpdateProvider(indexEditor)));
        } catch (CommitFailedException e) {
            throw new RuntimeException(e);
        }
    }
}
