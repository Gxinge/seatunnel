/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.api.sink;

import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Optional;

import static org.apache.seatunnel.api.common.SeaTunnelAPIErrorCode.SINK_TABLE_NOT_EXIST;
import static org.apache.seatunnel.api.common.SeaTunnelAPIErrorCode.SOURCE_ALREADY_HAS_DATA;

@AllArgsConstructor
@Slf4j
public class DefaultSaveModeHandler implements SaveModeHandler {

    @Nonnull public SchemaSaveMode schemaSaveMode;
    @Nonnull public DataSaveMode dataSaveMode;
    @Nonnull public Catalog catalog;
    @Nonnull public TablePath tablePath;
    @Nullable public CatalogTable catalogTable;
    @Nullable public String customSql;

    public DefaultSaveModeHandler(
            SchemaSaveMode schemaSaveMode,
            DataSaveMode dataSaveMode,
            Catalog catalog,
            CatalogTable catalogTable,
            String customSql) {
        this(
                schemaSaveMode,
                dataSaveMode,
                catalog,
                catalogTable.getTableId().toTablePath(),
                catalogTable,
                customSql);
    }

    @Override
    public void handleSchemaSaveMode() {
        switch (schemaSaveMode) {
            case RECREATE_SCHEMA:
                recreateSchema();
                break;
            case CREATE_SCHEMA_WHEN_NOT_EXIST:
                createSchemaWhenNotExist();
                break;
            case ERROR_WHEN_SCHEMA_NOT_EXIST:
                errorWhenSchemaNotExist();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported save mode: " + schemaSaveMode);
        }
    }

    @Override
    public void handleDataSaveMode() {
        switch (dataSaveMode) {
            case DROP_DATA:
                keepSchemaDropData();
                break;
            case APPEND_DATA:
                keepSchemaAndData();
                break;
            case CUSTOM_PROCESSING:
                customProcessing();
                break;
            case ERROR_WHEN_DATA_EXISTS:
                errorWhenDataExists();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported save mode: " + dataSaveMode);
        }
    }

    protected void recreateSchema() {
        if (tableExists()) {
            dropTable();
        }
        createTable();
    }

    protected void createSchemaWhenNotExist() {
        if (!tableExists()) {
            createTable();
        }
    }

    protected void errorWhenSchemaNotExist() {
        if (!tableExists()) {
            throw new SeaTunnelRuntimeException(SINK_TABLE_NOT_EXIST, "The sink table not exist");
        }
    }

    protected void keepSchemaDropData() {
        if (tableExists()) {
            truncateTable();
        }
    }

    protected void keepSchemaAndData() {}

    protected void customProcessing() {
        executeCustomSql();
    }

    protected void errorWhenDataExists() {
        if (dataExists()) {
            throw new SeaTunnelRuntimeException(
                    SOURCE_ALREADY_HAS_DATA, "The target data source already has data");
        }
    }

    protected boolean tableExists() {
        return catalog.tableExists(tablePath);
    }

    protected void dropTable() {
        try {
            log.info(
                    "Dropping table {} with action {}",
                    tablePath,
                    catalog.previewAction(
                            Catalog.ActionType.DROP_TABLE, tablePath, Optional.empty()));
        } catch (UnsupportedOperationException ignore) {
            log.info("Dropping table {}", tablePath);
        }
        catalog.dropTable(tablePath, true);
    }

    protected void createTable() {
        if (!catalog.databaseExists(tablePath.getDatabaseName())) {
            try {
                log.info(
                        "Creating database {} with action {}",
                        tablePath.getDatabaseName(),
                        catalog.previewAction(
                                Catalog.ActionType.CREATE_DATABASE, tablePath, Optional.empty()));
            } catch (UnsupportedOperationException ignore) {
                log.info("Creating database {}", tablePath.getDatabaseName());
            }
            catalog.createDatabase(tablePath, true);
        }
        try {
            log.info(
                    "Creating table {} with action {}",
                    tablePath,
                    catalog.previewAction(
                            Catalog.ActionType.CREATE_TABLE,
                            tablePath,
                            Optional.ofNullable(catalogTable)));
        } catch (UnsupportedOperationException ignore) {
            log.info("Creating table {}", tablePath);
        }
        catalog.createTable(tablePath, catalogTable, true);
    }

    protected void truncateTable() {
        try {
            log.info(
                    "Truncating table {} with action {}",
                    tablePath,
                    catalog.previewAction(
                            Catalog.ActionType.TRUNCATE_TABLE, tablePath, Optional.empty()));
        } catch (UnsupportedOperationException ignore) {
            log.info("Truncating table {}", tablePath);
        }
        catalog.truncateTable(tablePath, true);
    }

    protected boolean dataExists() {
        return catalog.isExistsData(tablePath);
    }

    protected void executeCustomSql() {
        log.info("Executing custom SQL for table {} with SQL: {}", tablePath, customSql);
        catalog.executeSql(tablePath, customSql);
    }

    @Override
    public TablePath getHandleTablePath() {
        return tablePath;
    }

    @Override
    public Catalog getHandleCatalog() {
        return catalog;
    }

    @Override
    public SchemaSaveMode getSchemaSaveMode() {
        return schemaSaveMode;
    }

    @Override
    public DataSaveMode getDataSaveMode() {
        return dataSaveMode;
    }

    @Override
    public void close() throws Exception {
        catalog.close();
    }
}
