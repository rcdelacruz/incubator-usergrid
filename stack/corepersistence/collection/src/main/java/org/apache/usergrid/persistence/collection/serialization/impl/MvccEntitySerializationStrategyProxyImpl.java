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
package org.apache.usergrid.persistence.collection.serialization.impl;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import org.apache.usergrid.persistence.collection.CollectionScope;
import org.apache.usergrid.persistence.collection.EntitySet;
import org.apache.usergrid.persistence.collection.MvccEntity;
import org.apache.usergrid.persistence.collection.mvcc.MvccEntityMigrationStrategy;
import org.apache.usergrid.persistence.collection.serialization.MvccEntitySerializationStrategy;
import org.apache.usergrid.persistence.core.astyanax.MultiTennantColumnFamilyDefinition;
import org.apache.usergrid.persistence.core.migration.data.DataMigrationManager;
import org.apache.usergrid.persistence.core.migration.data.MigrationInfoSerialization;
import org.apache.usergrid.persistence.model.entity.Id;

import java.util.*;


/**
 * Version 4 implementation of entity serialization. This will proxy writes and reads so that during
 * migration data goes to both sources and is read from the old source. After the upgrade completes,
 * it will be available from the new source
 */
public class MvccEntitySerializationStrategyProxyImpl implements MvccEntitySerializationStrategy {


    protected final Keyspace keyspace;
    private final MigrationInfoSerialization migrationInfoSerialization;
    private final


    @Inject
    public MvccEntitySerializationStrategyProxyImpl(final Keyspace keyspace,
                                                    final Set<MvccEntitySerializationStrategy> allVersions,
                                                    final MigrationInfoSerialization migrationInfoSerialization) {

        this.keyspace = keyspace;

        this.migrationInfoSerialization = migrationInfoSerialization;
    }


    @Override
    public MutationBatch write( final CollectionScope context, final MvccEntity entity ) {
        if ( isOldVersion() ) {
            final MutationBatch aggregateBatch = keyspace.prepareMutationBatch();

            aggregateBatch.mergeShallow( previous.write( context, entity ) );
            aggregateBatch.mergeShallow( current.write( context, entity ) );

            return aggregateBatch;
        }

        return current.write( context, entity );
    }


    @Override
    public EntitySet load( final CollectionScope scope, final Collection<Id> entityIds, final UUID maxVersion ) {
        if ( isOldVersion() ) {
                    return previous.load( scope, entityIds, maxVersion );
                }

                return current.load( scope, entityIds, maxVersion );
    }



    @Override
    public Iterator<MvccEntity> loadDescendingHistory( final CollectionScope context, final Id entityId,
                                                       final UUID version, final int fetchSize ) {
        if ( isOldVersion() ) {
            return previous.loadDescendingHistory( context, entityId, version, fetchSize );
        }

        return current.loadDescendingHistory( context, entityId, version, fetchSize );
    }


    @Override
    public Iterator<MvccEntity> loadAscendingHistory( final CollectionScope context, final Id entityId,
                                                      final UUID version, final int fetchSize ) {
        if ( isOldVersion() ) {
            return previous.loadAscendingHistory( context, entityId, version, fetchSize );
        }

        return current.loadAscendingHistory( context, entityId, version, fetchSize );
    }

    @Override
    public Optional<MvccEntity> load( final CollectionScope scope, final Id entityId ) {
        if ( isOldVersion() ) {
            return previous.load( scope, entityId );
        }

        return current.load( scope, entityId );
    }


    @Override
    public MutationBatch mark( final CollectionScope context, final Id entityId, final UUID version ) {
        if ( isOldVersion() ) {
            final MutationBatch aggregateBatch = keyspace.prepareMutationBatch();

            aggregateBatch.mergeShallow( previous.mark( context, entityId, version ) );
            aggregateBatch.mergeShallow( current.mark( context, entityId, version ) );

            return aggregateBatch;
        }

        return current.mark( context, entityId, version );
    }


    @Override
    public MutationBatch delete( final CollectionScope context, final Id entityId, final UUID version ) {
        if ( isOldVersion() ) {
            final MutationBatch aggregateBatch = keyspace.prepareMutationBatch();

            aggregateBatch.mergeShallow( previous.delete( context, entityId, version ) );
            aggregateBatch.mergeShallow( current.delete( context, entityId, version ) );

            return aggregateBatch;
        }

        return current.delete( context, entityId, version );
    }

    /**
     * Return true if we're on an old version
     */
    private boolean isOldVersion() {
        return migrationInfoSerialization.getCurrentVersion() < getVersion();
    }


    @Override
    public Collection<MultiTennantColumnFamilyDefinition> getColumnFamilies() {
        return Collections.emptyList();
    }


    @Override
    public int getImplementationVersion() {
        return 0;
    }
}

