/*
 * Copyright (c) 2009-2016, b3log.org & hacpai.com
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
package org.b3log.latke.repository;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.RuntimeDatabase;
import org.b3log.latke.RuntimeEnv;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.repository.jdbc.JDBCRepositoryException;
import org.b3log.latke.util.Callstacks;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Abstract repository.
 *
 * <p>
 * This is a base adapter for wrapped {@link #repository repository}, the underlying repository will be instantiated in
 * the {@link #AbstractRepository(java.lang.String) constructor} with
 * {@link Latkes#getRuntimeEnv() the current runtime environment}.
 * </p>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.2.1.7, Jan 25, 2016
 */
public abstract class AbstractRepository implements Repository {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AbstractRepository.class.getName());

    /**
     * Repository.
     */
    private Repository repository;

    /**
     * Constructs a repository with the specified name.
     *
     * @param name the specified name
     */
    @SuppressWarnings("unchecked")
    public AbstractRepository(final String name) {
        final RuntimeEnv runtimeEnv = Latkes.getRuntimeEnv();

        try {
            Class<Repository> repositoryClass = null;

            switch (runtimeEnv) {
                case LOCAL:
                    final RuntimeDatabase runtimeDatabase = Latkes.getRuntimeDatabase();

                    switch (runtimeDatabase) {
                        case MYSQL:
                        case H2:
                        case MSSQL:
                            repositoryClass = (Class<Repository>) Class.forName("org.b3log.latke.repository.jdbc.JdbcRepository");

                            break;
                        case REDIS:
                            repositoryClass = (Class<Repository>) Class.forName("org.b3log.latke.repository.redis.RedisRepository");

                            break;
                        case NONE:
                            repositoryClass = (Class<Repository>) Class.forName("org.b3log.latke.repository.NoneRepository");

                            break;
                        default:
                            throw new RuntimeException("The runtime database [" + runtimeDatabase + "] is not support NOW!");
                    }

                    break;
                default:
                    throw new RuntimeException("Latke runs in the hell.... Please set the enviornment correctly");
            }

            final Constructor<Repository> constructor = repositoryClass.getConstructor(String.class);

            repository = constructor.newInstance(name);
        } catch (final Exception e) {
            throw new RuntimeException("Can not initialize repository!", e);
        }

        Repositories.addRepository(repository);
        LOGGER.log(Level.INFO, "Constructed repository[name={0}]", name);
    }

    @Override
    public String add(final JSONObject jsonObject) throws RepositoryException {
        if (!isWritable() && !isInternalCall()) {
            throw new RepositoryException("The repository[name=" + getName() + "] is not writable at present");
        }

        Repositories.check(getName(), jsonObject, Keys.OBJECT_ID);

        return repository.add(jsonObject);
    }

    @Override
    public void update(final String id, final JSONObject jsonObject) throws RepositoryException {
        if (!isWritable() && !isInternalCall()) {
            throw new RepositoryException("The repository[name=" + getName() + "] is not writable at present");
        }

        Repositories.check(getName(), jsonObject, Keys.OBJECT_ID);

        repository.update(id, jsonObject);
    }

    @Override
    public void remove(final String id) throws RepositoryException {
        if (!isWritable() && !isInternalCall()) {
            throw new RepositoryException("The repository[name=" + getName() + "] is not writable at present");
        }

        repository.remove(id);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        try {
            return repository.get(id);
        } catch (final JDBCRepositoryException e) {
            LOGGER.log(Level.WARN, "SQL exception[msg={0}]", e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, JSONObject> get(final Iterable<String> ids) throws RepositoryException {
        return repository.get(ids);
    }

    @Override
    public boolean has(final String id) throws RepositoryException {
        return repository.has(id);
    }

    @Override
    public JSONObject get(final Query query) throws RepositoryException {
        try {
            return repository.get(query);
        } catch (final JDBCRepositoryException e) {
            LOGGER.log(Level.WARN, "SQL exception[msg={0}, repository={1}, query={2}]", e.getMessage(), repository.getName(),
                    query.toString());

            final JSONObject ret = new JSONObject();
            final JSONObject pagination = new JSONObject();

            ret.put(Pagination.PAGINATION, pagination);
            pagination.put(Pagination.PAGINATION_PAGE_COUNT, 0);
            final JSONArray results = new JSONArray();

            ret.put(Keys.RESULTS, results);

            return ret;
        }
    }

    @Override
    public List<JSONObject> select(final String statement) throws RepositoryException {
        try {
            return repository.select(statement);
        } catch (final JDBCRepositoryException e) {
            LOGGER.log(Level.WARN, "SQL exception[msg={0}, repository={1}, statement={2}]", e.getMessage(), repository.getName(),
                    statement);

            return Collections.emptyList();
        }
    }

    @Override
    public List<JSONObject> getRandomly(final int fetchSize) throws RepositoryException {
        return repository.getRandomly(fetchSize);
    }

    @Override
    public long count() throws RepositoryException {
        return repository.count();
    }

    @Override
    public long count(final Query query) throws RepositoryException {
        return repository.count(query);
    }

    @Override
    public Transaction beginTransaction() {
        return repository.beginTransaction();
    }

    @Override
    public boolean hasTransactionBegun() {
        return repository.hasTransactionBegun();
    }

    @Override
    public String getName() {
        return repository.getName();
    }

    @Override
    public boolean isWritable() {
        return repository.isWritable();
    }

    @Override
    public void setWritable(final boolean writable) {
        repository.setWritable(writable);
    }

    /**
     * Gets the underlying repository.
     *
     * @return underlying repository
     */
    protected Repository getUnderlyingRepository() {
        return repository;
    }

    /**
     * Checks the current method is whether invoked as internal call.
     *
     * @return {@code true} if the current method is invoked as internal call, return {@code false} otherwise
     */
    private static boolean isInternalCall() {
        return Callstacks.isCaller("org.b3log.latke.remote.RepositoryAccessor", "*");
    }
}
