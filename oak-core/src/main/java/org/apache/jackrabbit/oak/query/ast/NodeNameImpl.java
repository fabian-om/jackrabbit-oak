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
package org.apache.jackrabbit.oak.query.ast;

import javax.jcr.PropertyType;

import org.apache.jackrabbit.oak.api.PropertyValue;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.namepath.JcrNameParser;
import org.apache.jackrabbit.oak.query.index.FilterImpl;
import org.apache.jackrabbit.oak.spi.query.PropertyValues;
import org.apache.jackrabbit.util.ISO9075;

/**
 * The function "name(..)".
 */
public class NodeNameImpl extends DynamicOperandImpl {

    private final String selectorName;
    private SelectorImpl selector;

    public NodeNameImpl(String selectorName) {
        this.selectorName = selectorName;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        return "name(" + quote(selectorName) + ')';
    }

    public void bindSelector(SourceImpl source) {
        selector = source.getExistingSelector(selectorName);
    }

    @Override
    public boolean supportsRangeConditions() {
        return false;
    }
    
    @Override
    public PropertyExistenceImpl getPropertyExistence() {
        return null;
    }

    @Override
    public PropertyValue currentProperty() {
        String path = selector.currentPath();
        // Name escaping (convert space to _x0020_)
        String name = ISO9075.encode(PathUtils.getName(path));
        return PropertyValues.newName(name);
    }

    @Override
    public void restrict(FilterImpl f, Operator operator, PropertyValue v) {
        if (v == null) {
            return;
        }
        String name = getName(v);
        if (name == null) {
            throw new IllegalArgumentException("Invalid name value: " + v.toString());
        }
        // TODO support NAME(..) index conditions
    }

    @Override
    public boolean canRestrictSelector(SelectorImpl s) {
        return s == selector;
    }

    /**
     * Validate that the given value can be converted to a JCR name, and
     * return the name.
     *
     * @param v the value
     * @return name value, or {@code null} if the value can not be converted
     */
    private String getName(PropertyValue v) {
        // TODO correctly validate JCR names - see JCR 2.0 spec 3.2.4 Naming Restrictions
        switch (v.getType().tag()) {
        case PropertyType.DATE:
        case PropertyType.DECIMAL:
        case PropertyType.DOUBLE:
        case PropertyType.LONG:
        case PropertyType.BOOLEAN:
            return null;
        }
        String name = v.getValue(Type.NAME);
        // Name escaping (convert _x0020_ to space)
        name = ISO9075.decode(name);
        // normalize paths (./name > name)
        name = PropertyValues.getOakPath(name, query.getNamePathMapper());

        if (name.startsWith("[") && !name.endsWith("]")) {
            return null;
        } else if (!JcrNameParser.validate(name)) {
            return null;
        }
        return name;
    }
    
    @Override
    int getPropertyType() {
        return PropertyType.NAME;
    }

}
