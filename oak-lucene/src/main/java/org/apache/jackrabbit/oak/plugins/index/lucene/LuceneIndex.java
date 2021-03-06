/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.index.lucene;

import static org.apache.jackrabbit.JcrConstants.JCR_MIXINTYPES;
import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.INDEX_DEFINITIONS_NAME;
import static org.apache.jackrabbit.oak.plugins.index.IndexConstants.TYPE_PROPERTY_NAME;
import static org.apache.jackrabbit.oak.plugins.index.IndexUtils.getString;
import static org.apache.jackrabbit.oak.plugins.index.lucene.FieldNames.PATH;
import static org.apache.jackrabbit.oak.plugins.index.lucene.FieldNames.PATH_SELECTOR;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.INDEX_DATA_CHILD_NAME;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.PERSISTENCE_PATH;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.PERSISTENCE_FILE;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.PERSISTENCE_NAME;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.PERSISTENCE_OAK;
import static org.apache.jackrabbit.oak.plugins.index.lucene.LuceneIndexConstants.TYPE_LUCENE;
import static org.apache.jackrabbit.oak.plugins.index.lucene.TermFactory.newFulltextTerm;
import static org.apache.jackrabbit.oak.plugins.index.lucene.TermFactory.newPathTerm;
import static org.apache.jackrabbit.oak.query.Query.JCR_PATH;
import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.spi.query.Cursor;
import org.apache.jackrabbit.oak.spi.query.Cursors;
import org.apache.jackrabbit.oak.spi.query.Filter;
import org.apache.jackrabbit.oak.spi.query.Filter.PropertyRestriction;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.query.QueryIndex.FulltextQueryIndex;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.ReadOnlyBuilder;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a QueryIndex that does lookups against a Lucene-based index
 * 
 * <p>
 * To define a lucene index on a subtree you have to add an
 * <code>oak:index<code> node.
 * 
 * Under it follows the index definition node that:
 * <ul>
 * <li>must be of type <code>oak:queryIndexDefinition</code></li>
 * <li>must have the <code>type</code> property set to <b><code>lucene</code>
 * </b></li>
 * </ul>
 * </p>
 * 
 * <p>
 * Note: <code>reindex<code> is a property that when set to <code>true</code>,
 * triggers a full content reindex.
 * </p>
 * 
 * <pre>
 * <code>
 * {
 *     NodeBuilder index = root.child("oak:index");
 *     index.child("lucene")
 *         .setProperty("jcr:primaryType", "oak:queryIndexDefinition", Type.NAME)
 *         .setProperty("type", "lucene")
 *         .setProperty("reindex", "true");
 * }
 * </code>
 * </pre>
 * 
 * @see QueryIndex
 * 
 */
public class LuceneIndex implements FulltextQueryIndex {

    private static final Logger LOG = LoggerFactory
            .getLogger(LuceneIndex.class);

    public LuceneIndex() {
    }

    @Override
    public String getIndexName() {
        return "lucene";
    }

    @Override
    public double getCost(Filter filter, NodeState root) {
        // TODO: proper cost calculation
        if (!isLive(root)) {
            // unusable index
            return Double.POSITIVE_INFINITY;
        }
        if (!filter.getFulltextConditions().isEmpty()) {
            return 0.5;
        }
        // no fulltext, don't use this index
        return Double.POSITIVE_INFINITY;
    }

    private static boolean isLive(NodeState root) {
        NodeState def = getIndexDef(root);
        if (def == null) {
            return false;
        }
        String type = getString(def, PERSISTENCE_NAME);
        if (type == null || PERSISTENCE_OAK.equalsIgnoreCase(type)) {
            return getIndexDataNode(def) != null;
        }

        if (PERSISTENCE_FILE.equalsIgnoreCase(type)) {
            return getString(def, PERSISTENCE_PATH) != null;
        }

        return false;
    }

    private static Directory newDirectory(NodeState root) {
        NodeState def = getIndexDef(root);
        if (def == null) {
            return null;
        }

        String type = getString(def, PERSISTENCE_NAME);
        if (type == null || PERSISTENCE_OAK.equalsIgnoreCase(type)) {
            NodeState index = getIndexDataNode(def);
            if (index == null) {
                return null;
            }
            return new ReadOnlyOakDirectory(new ReadOnlyBuilder(index));
        }

        if (PERSISTENCE_FILE.equalsIgnoreCase(type)) {
            String fs = getString(def, PERSISTENCE_PATH);
            if (fs == null) {
                return null;
            }
            File f = new File(fs);
            if (!f.exists()) {
                return null;
            }
            try {
                // TODO lock factory
                return FSDirectory.open(f);
            } catch (IOException e) {
                LOG.error("Unable to open directory {}", fs);
            }
        }

        return null;
    }

    private static NodeState getIndexDef(NodeState node) {
        NodeState state = node.getChildNode(INDEX_DEFINITIONS_NAME);
        for (ChildNodeEntry entry : state.getChildNodeEntries()) {
            NodeState ns = entry.getNodeState();
            if (TYPE_LUCENE.equals(getString(ns, TYPE_PROPERTY_NAME))) {
                return ns;
            }
        }
        return null;
    }

    private static NodeState getIndexDataNode(NodeState node) {
        if (node.hasChildNode(INDEX_DATA_CHILD_NAME)) {
            return node.getChildNode(INDEX_DATA_CHILD_NAME);
        }
        // unusable index (not initialized yet)
        return null;
    }

    @Override
    public String getPlan(Filter filter, NodeState root) {
        return getQuery(filter, root, null).toString();
    }

    @Override
    public Cursor query(Filter filter, NodeState root) {
        Directory directory = newDirectory(root);
        if (directory == null) {
            return Cursors.newPathCursor(Collections.<String> emptySet());
        }
        long s = System.currentTimeMillis();
        try {
            try {
                IndexReader reader = DirectoryReader.open(directory);
                try {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    Collection<String> paths = new ArrayList<String>();

                    Query query = getQuery(filter, root, reader);
                    if (query != null) {
                        TopDocs docs = searcher
                                .search(query, Integer.MAX_VALUE);
                        for (ScoreDoc doc : docs.scoreDocs) {
                            String path = reader.document(doc.doc,
                                    PATH_SELECTOR).get(PATH);
                            if ("".equals(path)) {
                                paths.add("/");
                            } else if (path != null) {
                                paths.add(path);
                            }
                        }
                    }
                    LOG.debug("query via {} took {} ms.", this,
                            System.currentTimeMillis() - s);
                    return Cursors.newPathCursor(paths);
                } finally {
                    reader.close();
                }
            } finally {
                directory.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Cursors.newPathCursor(Collections.<String> emptySet());
        }
    }

    private static Query getQuery(Filter filter, NodeState root,
            IndexReader reader) {
        List<Query> qs = new ArrayList<Query>();

        if (!filter.matchesAllTypes()) {
            addNodeTypeConstraints(qs, filter);
        }

        String path = filter.getPath();
        switch (filter.getPathRestriction()) {
        case ALL_CHILDREN:
            if ("/".equals(path)) {
                break;
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            qs.add(new PrefixQuery(newPathTerm(path)));
            break;
        case DIRECT_CHILDREN:
            // FIXME OAK-420
            if (!path.endsWith("/")) {
                path += "/";
            }
            qs.add(new PrefixQuery(newPathTerm(path)));
            break;
        case EXACT:
            qs.add(new TermQuery(newPathTerm(path)));
            break;
        case PARENT:
            if (PathUtils.denotesRoot(path)) {
                // there's no parent of the root node
                return null;
            }
            qs.add(new TermQuery(newPathTerm(PathUtils.getParentPath(path))));
            break;
        case NO_RESTRICTION:
            break;
        }

        for (PropertyRestriction pr : filter.getPropertyRestrictions()) {
            String name = pr.propertyName;
            if (name.contains("/")) {
                // lucene cannot handle child-level property restrictions
                continue;
            }
            if ("rep:excerpt".equals(name)) {
                continue;
            }

            String first = null;
            String last = null;
            boolean isLike = pr.isLike;

            // TODO what to do with escaped tokens?
            if (pr.first != null) {
                first = pr.first.getValue(Type.STRING);
                first = first.replace("\\", "");
            }
            if (pr.last != null) {
                last = pr.last.getValue(Type.STRING);
                last = last.replace("\\", "");
            }

            if (isLike) {
                first = first.replace('%', WildcardQuery.WILDCARD_STRING);
                first = first.replace('_', WildcardQuery.WILDCARD_CHAR);

                int indexOfWS = first.indexOf(WildcardQuery.WILDCARD_STRING);
                int indexOfWC = first.indexOf(WildcardQuery.WILDCARD_CHAR);
                int len = first.length();

                if (indexOfWS == len || indexOfWC == len) {
                    // remove trailing "*" for prefixquery
                    first = first.substring(0, first.length() - 1);
                    if (JCR_PATH.equals(name)) {
                        qs.add(new PrefixQuery(newPathTerm(first)));
                    } else {
                        qs.add(new PrefixQuery(new Term(name, first)));
                    }
                } else {
                    if (JCR_PATH.equals(name)) {
                        qs.add(new WildcardQuery(newPathTerm(first)));
                    } else {
                        qs.add(new WildcardQuery(new Term(name, first)));
                    }
                }
                continue;
            }

            if (first != null && first.equals(last) && pr.firstIncluding
                    && pr.lastIncluding) {
                if (JCR_PATH.equals(name)) {
                    qs.add(new TermQuery(newPathTerm(first)));
                } else {
                    if ("*".equals(name)) {
                        addReferenceConstraint(first, qs, reader);
                    } else {
                        qs.add(new TermQuery(new Term(name, first)));
                    }
                }
                continue;
            }

            qs.add(TermRangeQuery.newStringRange(name, first, last,
                    pr.firstIncluding, pr.lastIncluding));
        }

        addFulltextConstraints(qs, filter);

        if (qs.size() == 0) {
            return new MatchAllDocsQuery();
        }
        if (qs.size() == 1) {
            return qs.get(0);
        }
        BooleanQuery bq = new BooleanQuery();
        for (Query q : qs) {
            bq.add(q, MUST);
        }
        return bq;
    }

    private static void addReferenceConstraint(String uuid, List<Query> qs,
            IndexReader reader) {
        if (reader == null) {
            // getPlan call
            qs.add(new TermQuery(new Term("*", uuid)));
            return;
        }

        // reference query
        BooleanQuery bq = new BooleanQuery();
        Collection<String> fields = MultiFields.getIndexedFields(reader);
        for (String f : fields) {
            bq.add(new TermQuery(new Term(f, uuid)), SHOULD);
        }
        qs.add(bq);
    }

    private static void addNodeTypeConstraints(List<Query> qs, Filter filter) {
        BooleanQuery bq = new BooleanQuery();
        for (String type : filter.getPrimaryTypes()) {
            bq.add(new TermQuery(new Term(JCR_PRIMARYTYPE, type)), SHOULD);
        }
        for (String type : filter.getMixinTypes()) {
            bq.add(new TermQuery(new Term(JCR_MIXINTYPES, type)), SHOULD);
        }
        qs.add(bq);
    }

    private static void addFulltextConstraints(List<Query> qs, Filter filter) {
        if (filter.getFulltextConditions() == null
                || filter.getFulltextConditions().isEmpty()) {
            return;
        }
        List<String> tokens = tokenize(filter.getFulltextConditions()
                .iterator().next().toLowerCase());
        if (tokens.size() == 1) {
            String token = tokens.get(0);
            if (token.contains(" ")) {
                PhraseQuery pq = new PhraseQuery();
                for (String t : token.split(" ")) {
                    pq.add(newFulltextTerm(t));
                }
                qs.add(pq);
            } else {
                if (!token.endsWith("*")) {
                    token = token + "*";
                }
                qs.add(new WildcardQuery(newFulltextTerm(token)));
            }
            return;
        }

        BooleanQuery q = new BooleanQuery();
        Iterator<String> iterator = tokens.iterator();
        while (iterator.hasNext()) {
            String token = iterator.next();
            q.add(tokenToQuery(token), MUST);
        }
        qs.add(q);
    }

    private static Query tokenToQuery(String token) {
        if (token.contains(" ")) {
            PhraseQuery pq = new PhraseQuery();
            for (String t : token.split(" ")) {
                pq.add(newFulltextTerm(t));
            }
            return pq;
        }
        return new TermQuery(newFulltextTerm(token));
    }

    /**
     * 
     * inspired from lucene's WildcardQuery#toAutomaton
     */
    private static List<String> tokenize(String in) {
        List<String> out = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < in.length();) {
            final int c = in.codePointAt(i);
            int length = Character.charCount(c);
            switch (c) {
            case ' ':
            case '&':
                if (quote) {
                    token.append(' ');
                } else if (token.length() > 0) {
                    out.add(token.toString());
                    token = new StringBuilder();
                }
                break;
            case '"':
            case '\'':
                if (quote) {
                    quote = false;
                    if (token.length() > 0) {
                        out.add(token.toString());
                        token = new StringBuilder();
                    }
                } else {
                    quote = true;
                }
                break;
            case '\\':
                if (i + length < in.length()) {
                    final int nextChar = in.codePointAt(i + length);
                    length += Character.charCount(nextChar);
                    token.append(new String(Character.toChars(nextChar)));
                    break;
                }
            default:
                token.append(new String(Character.toChars(c)));
            }
            i += length;
        }
        if (token.length() > 0) {
            out.add(token.toString());
        }
        return out;
    }

}
