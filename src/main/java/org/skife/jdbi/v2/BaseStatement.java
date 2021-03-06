/*
 * Copyright (C) 2004 - 2014 Brian McCallister
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.skife.jdbi.v2;

import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.tweak.BaseStatementCustomizer;
import org.skife.jdbi.v2.tweak.StatementCustomizer;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

abstract class BaseStatement
{
    private final Collection<StatementCustomizer> customizers = new ArrayList<StatementCustomizer>();
    private final ConcreteStatementContext context;
    private final Foreman                  foreman;

    protected BaseStatement(final ConcreteStatementContext context, Foreman foreman)
    {
        this.context = context;
        this.foreman = foreman.createChild();
        addCustomizer(new StatementCleaningCustomizer());
    }

    protected final Foreman getForeman() {
        return foreman;
    }

    protected final ConcreteStatementContext getConcreteContext()
    {
        return this.context;
    }

    /**
     * Obtain the statement context associated with this statement
     */
    public final StatementContext getContext()
    {
        return context;
    }

    protected void addCustomizers(final Collection<StatementCustomizer> customizers)
    {
        this.customizers.addAll(customizers);
    }

    protected void addCustomizer(final StatementCustomizer customizer)
    {
        this.customizers.add(customizer);
    }

    protected Collection<StatementCustomizer> getStatementCustomizers()
    {
        return this.customizers;
    }

    protected final void beforeExecution(final PreparedStatement stmt)
    {
        for (StatementCustomizer customizer : customizers) {
            try {
                customizer.beforeExecution(stmt, context);
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Exception thrown in statement customization", e, context);
            }
        }
    }

    protected final void afterExecution(final PreparedStatement stmt)
    {
        for (StatementCustomizer customizer : customizers) {
            try {
                customizer.afterExecution(stmt, context);
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Exception thrown in statement customization", e, context);
            }
        }
    }

    protected final void cleanup()
    {
        for (StatementCustomizer customizer : customizers) {
            try {
                customizer.cleanup(context);
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Could not clean up", e, context);
            }
        }
    }

    protected void addCleanable(final Cleanable cleanable)
    {
        this.context.getCleanables().add(cleanable);
    }

    class StatementCleaningCustomizer extends BaseStatementCustomizer
    {
        @Override
        public final void cleanup(final StatementContext ctx)
            throws SQLException
        {
            final List<SQLException> exceptions = new ArrayList<SQLException>();
            try {
                Collections.reverse(context.getCleanables());
                for (Cleanable cleanable : context.getCleanables()) {
                    try {
                        cleanable.cleanup();
                    }
                    catch (SQLException sqlException) {
                        exceptions.add(sqlException);
                    }
                }
                context.getCleanables().clear();
            }
            finally {
                if (exceptions.size() > 1) {
                    // Chain multiple SQLExceptions together to be one big exceptions.
                    // (Wonder if that actually works...)
                    for (int i = 0; i < (exceptions.size() - 1); i++) {
                        exceptions.get(i).setNextException(exceptions.get(i + 1));
                    }
                }
                if (exceptions.size() > 0) {
                    throw exceptions.get(0);
                }
            }
        }
    }
}

