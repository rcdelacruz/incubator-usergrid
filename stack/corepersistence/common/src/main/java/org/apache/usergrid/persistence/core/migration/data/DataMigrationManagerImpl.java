/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.core.migration.data;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.persistence.core.migration.data.newimpls.MigrationPlugin;
import org.apache.usergrid.persistence.core.migration.data.newimpls.ProgressObserver;
import org.apache.usergrid.persistence.core.migration.schema.MigrationException;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;


@Singleton
public class DataMigrationManagerImpl implements DataMigrationManager {

    private static final Logger LOG = LoggerFactory.getLogger( DataMigrationManagerImpl.class );

    private final Map<String, MigrationPlugin> migrationPlugins = new HashMap<>();

    private final MigrationInfoSerialization migrationInfoSerialization;


    /**
     * Cache to cache versions temporarily
     */
    private final LoadingCache<String, Integer> versionCache = CacheBuilder.newBuilder()
            //cache the local value for 1 minute
            .expireAfterWrite( 1, TimeUnit.MINUTES ).build( new CacheLoader<String, Integer>() {
                @Override
                public Integer load( final String key ) throws Exception {
                    return migrationInfoSerialization.getVersion( key );
                }
            } );


    @Inject
    public DataMigrationManagerImpl( final Set<MigrationPlugin> plugins,
                                     final MigrationInfoSerialization migrationInfoSerialization ) {

        Preconditions.checkNotNull( plugins, "plugins must not be null" );
        Preconditions.checkNotNull( migrationInfoSerialization, "migrationInfoSerialization must not be null" );

        this.migrationInfoSerialization = migrationInfoSerialization;


        for ( MigrationPlugin plugin : plugins ) {
            final String name = plugin.getName();


            final MigrationPlugin existing = migrationPlugins.get( name );

            if ( existing != null ) {
                throw new IllegalArgumentException( "Duplicate plugin name detected.  A plugin with name " + name
                        + " is already implemented by class '" + existing.getClass().getName() + "'.  Class '" + plugin
                        .getClass().getName() + "' is also trying to implement this name." );
            }

            migrationPlugins.put( name, plugin );
        }
    }


    @Override
    public void migrate() throws MigrationException {

        /**
         * Invoke each plugin to attempt a migration
         */
        for(final MigrationPlugin plugin: migrationPlugins.values()){
            final ProgressObserver observer = new CassandraProgressObserver(plugin.getName());

            plugin.run( observer );
        }


    }


    @Override
    public boolean isRunning() {

        for(final String pluginName :getPluginNames()){
           if( migrationInfoSerialization.getStatusCode(pluginName) == StatusCode.RUNNING.status){
               return true;
           }
        }


        return false;
    }


    @Override
    public void invalidate() {
        versionCache.invalidateAll();
    }


    @Override
    public int getCurrentVersion( final String pluginName ) {
        Preconditions.checkNotNull( pluginName, "pluginName cannot be null" );
        return migrationInfoSerialization.getVersion( pluginName );
    }


    @Override
    public void resetToVersion( final String pluginName, final int version ) {
        Preconditions.checkNotNull( pluginName, "pluginName cannot be null" );

        final MigrationPlugin plugin = migrationPlugins.get( pluginName );

        Preconditions.checkArgument( plugin != null, "Plugin " + pluginName + " could not be found" );

        final int highestAllowed = plugin.getMaxVersion();

        Preconditions.checkArgument( version <= highestAllowed,
                "You cannot set a version higher than the max of " + highestAllowed );
        Preconditions.checkArgument( version >= 0, "You must specify a version of 0 or greater" );

        migrationInfoSerialization.setVersion( pluginName, version );
    }


    @Override
    public String getLastStatus( final String pluginName ) {
        Preconditions.checkNotNull( pluginName, "pluginName cannot be null" );
        return migrationInfoSerialization.getStatusMessage( pluginName );
    }


    @Override
    public Set<String> getPluginNames() {
        return migrationPlugins.keySet();
    }


    /**
     * Different status enums
     */
    public enum StatusCode {
        COMPLETE( 1 ),
        RUNNING( 2 ),
        ERROR( 3 );

        public final int status;


        StatusCode( final int status ) {this.status = status;}
    }


    private final class CassandraProgressObserver implements ProgressObserver {

        private final String pluginName;

        private boolean failed = false;


        private CassandraProgressObserver( final String pluginName ) {this.pluginName = pluginName;}


        @Override
        public void failed( final int migrationVersion, final String reason ) {

            final String storedMessage = String.format( "Failed to migrate, reason is appended.  Error '%s'", reason );


            update( migrationVersion, storedMessage );

            LOG.error( storedMessage );

            failed = true;

            migrationInfoSerialization.setStatusCode( pluginName, StatusCode.ERROR.status );
        }


        @Override
        public void failed( final int migrationVersion, final String reason, final Throwable throwable ) {
            StringWriter stackTrace = new StringWriter();
            throwable.printStackTrace( new PrintWriter( stackTrace ) );


            final String storedMessage = String.format( "Failed to migrate, reason is appended.  Error '%s' %s", reason,
                    stackTrace.toString() );

            update( migrationVersion, storedMessage );


            LOG.error( "Unable to migrate version {} due to reason {}.", migrationVersion, reason, throwable );

            failed = true;

            migrationInfoSerialization.setStatusCode( pluginName, StatusCode.ERROR.status );
        }


        @Override
        public void update( final int migrationVersion, final String message ) {
            final String formattedOutput = String.format( "Migration version %d.  %s", migrationVersion, message );

            //Print this to the info log
            LOG.info( formattedOutput );

            migrationInfoSerialization.setStatusMessage( pluginName, formattedOutput );
        }


        /**
         * Return true if we failed
         */
        public boolean isFailed() {
            return failed;
        }
    }
}
