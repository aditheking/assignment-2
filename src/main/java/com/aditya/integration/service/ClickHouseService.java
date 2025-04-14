package com.aditya.integration.service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import com.aditya.integration.model.ClickHouseConnectionRequest;
import com.aditya.integration.model.ColumnRequest;
import com.aditya.integration.model.PreviewData;
import com.aditya.integration.model.PreviewRequest;

public interface ClickHouseService {

    void testConnection(ClickHouseConnectionRequest connectionRequest) throws SQLException;

    List<String> listTables(ClickHouseConnectionRequest connectionRequest) throws SQLException;

    List<String> listColumns(ColumnRequest columnRequest) throws SQLException;

    PreviewData fetchPreviewData(PreviewRequest previewRequest, int limit) throws SQLException;

    long streamTableData(ClickHouseConnectionRequest connectionRequest,
                           String table,
                           List<String> columns,
                           Consumer<List<Object>> rowConsumer) throws SQLException;

}