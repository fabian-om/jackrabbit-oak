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
package org.apache.jackrabbit.oak.plugins.observation;

import static com.google.common.base.Objects.toStringHelper;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.core.ReadOnlyTree;
import org.apache.jackrabbit.oak.namepath.NamePathMapper;
import org.apache.jackrabbit.oak.plugins.nodetype.ReadOnlyNodeTypeManager;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * TODO document
 */
class EventFilter {

    private final ReadOnlyNodeTypeManager ntMgr;
    private final NamePathMapper namePathMapper;
    private final int eventTypes;
    private final String path;
    private final boolean deep;
    private final String[] uuids;
    private final String[] nodeTypeOakName;
    private final boolean noLocal;

    public EventFilter(ReadOnlyNodeTypeManager ntMgr,
            NamePathMapper namePathMapper, int eventTypes,
            String path, boolean deep, String[] uuids,
            String[] nodeTypeName, boolean noLocal)
            throws NoSuchNodeTypeException, RepositoryException {
        this.ntMgr = ntMgr;
        this.namePathMapper = namePathMapper;
        this.eventTypes = eventTypes;
        this.path = path;
        this.deep = deep;
        this.uuids = uuids;
        this.nodeTypeOakName = validateNodeTypeNames(nodeTypeName);
        this.noLocal = noLocal;
    }

    public boolean include(int eventType, String path, @Nullable NodeState associatedParentNode) {
        return include(eventType)
                && include(path)
                && (associatedParentNode == null || includeByType(new ReadOnlyTree(associatedParentNode)))
                && (associatedParentNode == null || includeByUuid(associatedParentNode));
    }

    public boolean includeChildren(String path) {
        return PathUtils.isAncestor(path, this.path) ||
                path.equals(this.path) ||
                deep && PathUtils.isAncestor(this.path, path);
    }

    public boolean excludeLocal() {
        return noLocal;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("types", eventTypes)
                .add("path", path)
                .add("deep", deep)
                .add("uuids", uuids)
                .add("node types", nodeTypeOakName)
                .add("noLocal", noLocal)
            .toString();
    }

    //-----------------------------< internal >---------------------------------

    private boolean include(int eventType) {
        return (this.eventTypes & eventType) != 0;
    }

    private boolean include(String path) {
        boolean equalPaths = this.path.equals(path);
        if (!deep && !equalPaths) {
            return false;
        }
        if (deep && !(PathUtils.isAncestor(this.path, path) || equalPaths)) {
            return false;
        }
        return true;
    }

    /**
     * Checks whether to include an event based on the type of the associated
     * parent node and the node type filter.
     *
     * @param associatedParentNode the associated parent node of the event.
     * @return whether to include the event based on the type of the associated
     *         parent node.
     */
    private boolean includeByType(Tree associatedParentNode) {
        if (nodeTypeOakName == null) {
            return true;
        } else {
            for (String oakName : nodeTypeOakName) {
                if (ntMgr.isNodeType(associatedParentNode, oakName)) {
                    return true;
                }
            }
            // filter has node types set but none matched
            return false;
        }
    }

    private boolean includeByUuid(NodeState associatedParentNode) {
        if (uuids == null) {
            return true;
        }
        if (uuids.length == 0) {
            return false;
        }

        PropertyState uuidProperty = associatedParentNode.getProperty(JcrConstants.JCR_UUID);
        if (uuidProperty == null) {
            return false;
        }

        String parentUuid = uuidProperty.getValue(Type.STRING);
        for (String uuid : uuids) {
            if (parentUuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates the given node type names.
     *
     * @param nodeTypeNames the node type names.
     * @return the node type names as oak names.
     * @throws javax.jcr.nodetype.NoSuchNodeTypeException if one of the node type names refers to
     *                                 an non-existing node type.
     * @throws javax.jcr.RepositoryException     if an error occurs while reading from the
     *                                 node type manager.
     */
    @CheckForNull
    private String[] validateNodeTypeNames(@Nullable String[] nodeTypeNames)
            throws NoSuchNodeTypeException, RepositoryException {
        if (nodeTypeNames == null) {
            return null;
        }
        String[] oakNames = new String[nodeTypeNames.length];
        for (int i = 0; i < nodeTypeNames.length; i++) {
            ntMgr.getNodeType(nodeTypeNames[i]);
            oakNames[i] = namePathMapper.getOakName(nodeTypeNames[i]);
        }
        return oakNames;
    }
}
