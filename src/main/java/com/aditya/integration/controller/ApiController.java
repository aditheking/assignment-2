package com.aditya.integration.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aditya.integration.model.ApiResponse;
import com.aditya.integration.model.ClickHouseConnectionRequest;
import com.aditya.integration.model.ColumnRequest;
import com.aditya.integration.model.IngestionRequest;
import com.aditya.integration.model.IngestionResult;
import com.aditya.integration.model.PreviewData;
import com.aditya.integration.model.PreviewRequest;
import com.aditya.integration.service.ClickHouseService;
import com.aditya.integration.service.IngestionService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private static final int PREVIEW_LIMIT = 100;

    private final ClickHouseService clickHouseService;
    private final IngestionService ingestionService;

    @Autowired
    public ApiController(ClickHouseService clickHouseService, IngestionService ingestionService) {
        this.clickHouseService = clickHouseService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/clickhouse/connect")
    public ResponseEntity<ApiResponse<List<String>>> connectAndListTables(@RequestBody ClickHouseConnectionRequest request) {
        log.info("API: Connect/ListTables for {}:{} DB: {}", request.host(), request.port(), request.database());
        try {
            clickHouseService.testConnection(request);
            List<String> tables = clickHouseService.listTables(request);
            return ResponseEntity.ok(ApiResponse.success("Connection successful. Tables listed.", tables));
        } catch (Exception e) {
            log.error("API: Error connecting/listing tables: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(ApiResponse.error("Connection failed: " + e.getMessage()));
        }
    }

    @PostMapping("/clickhouse/columns")
    public ResponseEntity<ApiResponse<List<String>>> listColumns(@RequestBody ColumnRequest request) {
        log.info("API: ListColumns for table {}.{}", request.database(), request.table());
        try {
            List<String> columns = clickHouseService.listColumns(request);
            return ResponseEntity.ok(ApiResponse.success("Columns listed successfully.", columns));
        } catch (Exception e) {
            log.error("API: Error listing columns for {}.{}: {}", request.database(), request.table(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(ApiResponse.error("Failed to list columns: " + e.getMessage()));
        }
    }

    @PostMapping("/clickhouse/preview")
    public ResponseEntity<ApiResponse<PreviewData>> previewData(@RequestBody PreviewRequest request) {
        log.info("API: PreviewData for table {}.{}", request.database(), request.table());
        try {
            if (request.columns() == null || request.columns().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No columns selected for preview."));
            }
            PreviewData previewData = clickHouseService.fetchPreviewData(request, PREVIEW_LIMIT);
            return ResponseEntity.ok(ApiResponse.success("Data preview fetched successfully.", previewData));
        } catch (Exception e) {
            log.error("API: Error fetching preview data for {}.{}: {}", request.database(), request.table(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(ApiResponse.error("Failed to fetch preview data: " + e.getMessage()));
        }
    }

    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<IngestionResult>> startIngestion(@RequestBody IngestionRequest request) {
        log.info("API: StartIngestion: Direction={}, Source CH={}, Target FF={}",
                 request.direction(), request.chTable(), request.ffPath());
        try {
            if (request.direction() == null || (!request.direction().equals("ch_to_ff") && !request.direction().equals("ff_to_ch"))) {
                 return ResponseEntity.badRequest().body(ApiResponse.error("Invalid direction specified."));
            }
            if ("ch_to_ff".equals(request.direction()) && (request.columns() == null || request.columns().isEmpty())) {
                 log.warn("API: No columns specified for CH -> FF ingestion. Service will attempt to use all.");
            }

            IngestionResult result = ingestionService.performIngestion(request);
            return ResponseEntity.ok(ApiResponse.success("Ingestion completed successfully.", result));
        } catch (UnsupportedOperationException e) {
             log.warn("API: Attempted unsupported operation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                   .body(ApiResponse.error("Operation not supported: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
             log.warn("API: Invalid argument during ingestion: {}", e.getMessage());
            return ResponseEntity.badRequest()
                   .body(ApiResponse.error("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("API: Error during ingestion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(ApiResponse.error("Ingestion failed: " + e.getMessage()));
        }
    }
}