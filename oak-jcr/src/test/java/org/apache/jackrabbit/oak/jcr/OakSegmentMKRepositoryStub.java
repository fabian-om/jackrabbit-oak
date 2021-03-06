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
package org.apache.jackrabbit.oak.jcr;

import java.security.Principal;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.jcr.Credentials;
import javax.jcr.GuestCredentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.plugins.segment.mongo.MongoStore;
import org.apache.jackrabbit.test.NotExecutableException;
import org.apache.jackrabbit.test.RepositoryStub;

import com.mongodb.Mongo;

/**
 * A repository stub implementation for Oak on MongoMK
 */
public class OakSegmentMKRepositoryStub extends RepositoryStub {

    protected static final String HOST =
            System.getProperty("mongo.host", "127.0.0.1");

    protected static final int PORT =
            Integer.getInteger("mongo.port", 27017);

    protected static final String DB =
            System.getProperty("segment.db", "SegmentMK");

    private final Mongo connection;

    private final Repository repository;

    /**
     * Constructor as required by the JCR TCK.
     *
     * @param settings repository settings
     * @throws javax.jcr.RepositoryException If an error occurs.
     */
    public OakSegmentMKRepositoryStub(Properties settings) throws RepositoryException {
        super(settings);

        Session session = null;
        try {
            this.connection = new Mongo(HOST, PORT);
            Jcr jcr = new Jcr(new Oak(new SegmentNodeStore(
                    new MongoStore(connection.getDB(DB), 100 * 1024 * 1024))));
            jcr.with(Executors.newScheduledThreadPool(1));
            this.repository = jcr.createRepository();

            session = getRepository().login(superuser);
            TestContentLoader loader = new TestContentLoader();
            loader.loadTestContent(session);
        } catch (Exception e) {
            throw new RepositoryException(e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                connection.close();
            }
        }));
    }

    public static boolean isAvailable() {
        try {
            Mongo mongo = new Mongo(HOST, PORT);
            try {
                mongo.getDatabaseNames();
                return true;
            } finally {
                mongo.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the configured repository instance.
     *
     * @return the configured repository instance.
     */
    @Override
    public synchronized Repository getRepository() {
        return repository;
    }

    @Override
    public Credentials getReadOnlyCredentials() {
        return new GuestCredentials();
    }

    @Override
    public Principal getKnownPrincipal(Session session) throws RepositoryException {
        if (session instanceof JackrabbitSession) {
            PrincipalIterator principals = ((JackrabbitSession) session).getPrincipalManager().getPrincipals(PrincipalManager.SEARCH_TYPE_NOT_GROUP);
            if (principals.hasNext()) {
                return principals.nextPrincipal();
            }
        }

        throw new UnsupportedRepositoryOperationException();
    }

    private static final Principal UNKNOWN_PRINCIPAL = new Principal() {
        @Override
        public String getName() {
            return "an_unknown_user";
        }
    };

    @Override
    public Principal getUnknownPrincipal(Session session) throws RepositoryException, NotExecutableException {
        return UNKNOWN_PRINCIPAL;
    }

}
