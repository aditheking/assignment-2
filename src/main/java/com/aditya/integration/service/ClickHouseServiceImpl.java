package com.aditya.integration.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aditya.integration.model.ClickHouseConnectionRequest;
import com.aditya.integration.model.ColumnRequest;
import com.aditya.integration.model.PreviewData;
import com.aditya.integration.model.PreviewRequest;
import com.clickhouse.jdbc.ClickHouseDataSource;

@Service
public class ClickHouseServiceImpl implements ClickHouseService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseServiceImpl.class);

    private DataSource createDataSource(ClickHouseConnectionRequest req) throws SQLException {
        String protocol = (req.port() == 8443 || req.port() == 9440) ? "https" : "http";
        String url = String.format("jdbc:ch:%s://%s:%d/%s", protocol, req.host(), req.port(), req.database());
        log.info("Creating ClickHouse DataSource for: {}", url);

        Properties props = new Properties();
        props.setProperty("user", req.user());

        if (req.token() != null && !req.token().isBlank()) {
            log.debug("Using JWT Token authentication for user '{}'", req.user());
            props.setProperty("password", req.token());
        } else {
            log.warn("No JWT Token provided for user '{}'. Ensure password is not required.", req.user());
        }

        return new ClickHouseDataSource(url, props);
    }

    @Override
    public void testConnection(ClickHouseConnectionRequest req) throws SQLException {
        log.info("Testing connection to {}:{} DB: {}", req.host(), req.port(), req.database());
        try (Connection conn = createDataSource(req).getConnection()) {
            if (!conn.isValid(2)) {
                throw new SQLException("Connection validation failed after creation.");
            }
            log.info("Connection test successful.");
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<String> listTables(ClickHouseConnectionRequest req) throws SQLException {
        log.info("Listing tables in database: {}", req.database());
        List<String> tables = new ArrayList<>();
        String sql = "SHOW TABLES";

        try (Connection conn = createDataSource(req).getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            log.info("Found {} tables.", tables.size());
            return tables;
        } catch (SQLException e) {
            log.error("Failed to list tables: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<String> listColumns(ColumnRequest req) throws SQLException {
        log.info("Listing columns for table: {}.{}", req.database(), req.table());
        List<String> columns = new ArrayList<>();
        String sql = "SELECT name FROM system.columns WHERE database = ? AND table = ? ORDER BY position";

        ClickHouseConnectionRequest connReq = new ClickHouseConnectionRequest(
             req.host(), req.port(), req.database(), req.user(), req.token()
        );
        try (Connection conn = createDataSource(connReq).getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, req.database());
            pstmt.setString(2, req.table());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }
            log.info("Found {} columns for table {}.{}", columns.size(), req.database(), req.table());
            return columns;
        } catch (SQLException e) {
            log.error("Failed to list columns for {}.{}: {}", req.database(), req.table(), e.getMessage());
            throw e;
        }
    }

    @Override
    public PreviewData fetchPreviewData(PreviewRequest req, int limit) throws SQLException {
        log.info("Fetching preview (limit {}) for table: {}.{}", limit, req.database(), req.table());

        if (req.columns() == null || req.columns().isEmpty()) {
             log.warn("No columns provided for preview of {}.{}, returning empty.", req.database(), req.table());
             return new PreviewData(Collections.emptyList(), Collections.emptyList());
        }

        String selectedColumns = req.columns().stream()
                                    .map(this::quoteIdentifier)
                                    .collect(Collectors.joining(", "));

        String sql = String.format("SELECT %s FROM %s.%s LIMIT ?",
                                 selectedColumns,
                                 quoteIdentifier(req.database()),
                                 quoteIdentifier(req.table()));

        List<List<Object>> rows = new ArrayList<>();
        ClickHouseConnectionRequest connReq = new ClickHouseConnectionRequest(
            req.host(), req.port(), req.database(), req.user(), req.token()
        );
        try (Connection conn = createDataSource(connReq).getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            log.debug("Executing preview query: {}", pstmt.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
            log.info("Fetched {} rows for preview.", rows.size());
            return new PreviewData(req.columns(), rows);
        } catch (SQLException e) {
            log.error("Failed to fetch preview data for {}.{}: {}", req.database(), req.table(), e.getMessage());
            throw e;
        }
    }

    @Override
    public long streamTableData(ClickHouseConnectionRequest connectionRequest,
                                String table,
                                List<String> columns,
                                Consumer<List<Object>> rowConsumer) throws SQLException {

        log.info("Streaming data from {}.{} (Columns: {})", connectionRequest.database(), table, columns);

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Cannot stream data without specified columns.");
        }

        String selectedColumns = columns.stream()
                                      .map(this::quoteIdentifier)
                                      .collect(Collectors.joining(", "));

        String sql = String.format("SELECT %s FROM %s.%s",
                                 selectedColumns,
                                 quoteIdentifier(connectionRequest.database()),
                                 quoteIdentifier(table));

        long rowCount = 0;
        try (Connection conn = createDataSource(connectionRequest).getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            log.debug("Executing streaming query: {}", sql);
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rowConsumer.accept(row);
                rowCount++;
                if (rowCount % 50000 == 0) { 
                    log.info("Streamed {} rows from {}.{}...", rowCount, connectionRequest.database(), table);
                }
            }
            log.info("Finished streaming {}.{}. Total rows: {}", connectionRequest.database(), table, rowCount);
            return rowCount;
        } catch (SQLException e) {
            log.error("SQL error during data streaming from {}.{}: {}", connectionRequest.database(), table, e.getMessage());
            throw e;
        } catch (Exception e) {
             log.error("Error processing streamed row from {}.{}: {}", connectionRequest.database(), table, e.getMessage(), e);
            throw new SQLException("Error in row consumer during streaming", e);
        }
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier.replace("\"", "\\\"") + "\"";
    }
}
