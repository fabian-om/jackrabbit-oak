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
package org.apache.jackrabbit.oak.plugins.index.property.strategy;

import java.util.Set;

import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * Strategy that defines how the index content will be actually stored under the
 * index node
 */
public interface IndexStoreStrategy {

    /**
     * Updates the index for the given path.
     * 
     * @param index the index node
     * @param path path stored in the index
     * @param beforeKeys keys that no longer apply to the path
     * @param afterKeys keys that now do apply to the path
     */
    void update(
        NodeBuilder index, String path,
        Set<String> beforeKeys, Set<String> afterKeys);
    
    /**
     * Search for a given set of values.
     * 
     * @param filter the filter (used for logging)
     * @param indexName the name of the index (for logging)
     * @param index index node (may not be null)
     * @param values values to look for (null to check for property existence)
     * @return an iterator of paths
     */
    Iterable<String> query(Filter filter, String indexName, NodeState index, Iterable<String> values);

    /**
     * Count the occurrence of a given set of values. Used in calculating the
     * cost of an index.
     * 
     * @param index the index node (may not be null)
     * @param values values to look for (null to check for property existence)
     * @param max the maximum value to return
     * @return the aggregated count of occurrences for each provided value
     */
    int count(NodeState index, Set<String> values, int max);

}
