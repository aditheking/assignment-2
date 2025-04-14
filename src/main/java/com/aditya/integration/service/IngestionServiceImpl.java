package com.aditya.integration.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aditya.integration.model.ClickHouseConnectionRequest;
import com.aditya.integration.model.ColumnRequest;
import com.aditya.integration.model.IngestionRequest;
import com.aditya.integration.model.IngestionResult;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import com.opencsv.exceptions.CsvValidationException;

@Service
public class IngestionServiceImpl implements IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceImpl.class);
    private static final int BATCH_SIZE = 1000; // Configure batch size for inserts

    private final ClickHouseService clickHouseService;

    @Autowired
    public IngestionServiceImpl(ClickHouseService clickHouseService /*, FlatFileService flatFileService*/) {
        this.clickHouseService = clickHouseService;
        // this.flatFileService = flatFileService;
    }

    @Override
    public IngestionResult performIngestion(IngestionRequest request) throws Exception {
        log.info("Performing ingestion: Direction={}, CH Table={}, FF Path={}, File provided={}",
                 request.direction(), request.chTable(), request.ffPath(), (request.file() != null && !request.file().isEmpty()));

        if ("ch_to_ff".equalsIgnoreCase(request.direction())) {
            return ingestClickHouseToFlatFile(request);
        } else if ("ff_to_ch".equalsIgnoreCase(request.direction())) {
            // Validate necessary parameters for ff_to_ch
            if (request.file() == null || request.file().isEmpty()) {
                throw new IllegalArgumentException("Input file is required for Flat File to ClickHouse ingestion.");
            }
            if (request.chTable() == null || request.chTable().isBlank()) {
                throw new IllegalArgumentException("Target ClickHouse table is required.");
            }
            // Connection details validation could also be added here
            return ingestFlatFileToClickHouse(request);
        } else {
            throw new IllegalArgumentException("Invalid ingestion direction: " + request.direction());
        }
    }

    private IngestionResult ingestFlatFileToClickHouse(IngestionRequest request) throws IOException, SQLException, CsvValidationException {
        MultipartFile file = request.file();
        ClickHouseConnectionRequest chConn = new ClickHouseConnectionRequest(
            request.chHost(), request.chPort(), request.chDatabase(), request.chUser(), request.chToken()
        );
        String targetTable = request.chTable();
        char delimiter = request.ffDelimiter() != null && request.ffDelimiter().length() == 1 ?
                         request.ffDelimiter().charAt(0) : ICSVWriter.DEFAULT_SEPARATOR;

        AtomicLong recordsProcessed = new AtomicLong(0);
        Connection connection = null; // Manage connection manually for batching
        PreparedStatement pstmt = null;

        log.info("Starting FF -> CH: File={}, TargetTable={}, Delimiter='{}'",
                 file.getOriginalFilename(), targetTable, delimiter);

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReaderBuilder(reader)
                 .withSkipLines(1) // Assuming first line is header
                 .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(delimiter).build())
                 .build()) {

            // Read header (optional, but good for determining column count)
            // Reset reader and build a new CSVReader to get header
            try (Reader headerReader = new InputStreamReader(file.getInputStream());
                 CSVReader headerCsvReader = new CSVReaderBuilder(headerReader)
                     .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(delimiter).build())
                     .build()) {
                String[] headers = headerCsvReader.readNext();
                if (headers == null || headers.length == 0) {
                    throw new IOException("CSV file appears to be empty or header could not be read.");
                }
                log.info("CSV Headers detected: {}", Arrays.toString(headers));

                connection = clickHouseService.getConnection(chConn); 
                connection.setAutoCommit(false); // Essential for batching

                String placeholders = Arrays.stream(headers).map(h -> "?").collect(Collectors.joining(", "));
                String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                                           targetTable, String.join(", ", headers), placeholders);
                log.debug("Prepared SQL: {}", sql);
                pstmt = connection.prepareStatement(sql);

                String[] line;
                int currentBatchSize = 0;
                while ((line = csvReader.readNext()) != null) {
                    if (line.length != headers.length) {
                        log.warn("Skipping line {}: Expected {} columns, found {}. Data: {}",
                                 recordsProcessed.get() + 1, headers.length, line.length, Arrays.toString(line));
                        continue; // Skip rows with incorrect column count
                    }

                    for (int i = 0; i < line.length; i++) {
                        pstmt.setString(i + 1, line[i]);
                    }
                    pstmt.addBatch();
                    currentBatchSize++;
                    recordsProcessed.incrementAndGet();

                    if (currentBatchSize >= BATCH_SIZE) {
                        log.debug("Executing batch (size: {})", currentBatchSize);
                        pstmt.executeBatch();
                        connection.commit(); // Commit the batch
                        currentBatchSize = 0;
                        log.info("Processed {} records...", recordsProcessed.get());
                    }
                }

                if (currentBatchSize > 0) {
                    log.debug("Executing final batch (size: {})", currentBatchSize);
                    pstmt.executeBatch();
                    connection.commit();
                }

                log.info("FF -> CH Ingestion complete. Processed {} records into {}", recordsProcessed.get(), targetTable);
                return new IngestionResult(recordsProcessed.get());
            }
        } catch (IOException | SQLException | CsvValidationException e) {
            log.error("Error during FF -> CH ingestion: {}", e.getMessage(), e);
            if (connection != null) {
                try {
                    log.warn("Rolling back transaction due to error.");
                    connection.rollback();
                } catch (SQLException ex) {
                    log.error("Error rolling back transaction: {}", ex.getMessage(), ex);
                }
            }
            throw e; // Re-throw the exception to be handled by the controller
        } finally {
            // Clean up resources
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException e) {
                    log.error("Error closing PreparedStatement: {}", e.getMessage(), e);
                }
            }
            if (connection != null) {
                try {
                    connection.setAutoCommit(true); // Reset auto-commit
                    connection.close();
                } catch (SQLException e) {
                    log.error("Error closing Connection: {}", e.getMessage(), e);
                }
            }
        }
    }

    private IngestionResult ingestClickHouseToFlatFile(IngestionRequest request) throws SQLException, IOException {
        ClickHouseConnectionRequest chConn = new ClickHouseConnectionRequest(
            request.chHost(), request.chPort(), request.chDatabase(), request.chUser(), request.chToken()
        );

        char delimiter = request.ffDelimiter() != null && request.ffDelimiter().length() == 1 ?
                         request.ffDelimiter().charAt(0) : ICSVWriter.DEFAULT_SEPARATOR;

        AtomicLong recordsProcessed = new AtomicLong(0);

        // Ensure ffPath is not null or empty for writing
        if (request.ffPath() == null || request.ffPath().isBlank()) {
            throw new IllegalArgumentException("Target flat file path/name is required for ClickHouse to Flat File ingestion.");
        }

        try (Writer fileWriter = new FileWriter(request.ffPath());
             ICSVWriter csvWriter = new CSVWriterBuilder(fileWriter)
                .withSeparator(delimiter)
                .withQuoteChar(ICSVWriter.DEFAULT_QUOTE_CHARACTER)
                .withEscapeChar(ICSVWriter.DEFAULT_ESCAPE_CHARACTER)
                .withLineEnd(ICSVWriter.DEFAULT_LINE_END)
                .build()) {

            List<String> headers = request.columns();
            if (headers == null || headers.isEmpty()) {
                 log.warn("No columns specified for {}.{}, fetching all columns.", request.chDatabase(), request.chTable());
                 ColumnRequest colReq = new ColumnRequest(request.chHost(), request.chPort(), request.chDatabase(), request.chUser(), request.chToken(), request.chTable());
                 // Ensure chTable is provided when fetching columns
                 if (request.chTable() == null || request.chTable().isBlank()) {
                     throw new IllegalArgumentException("ClickHouse source table must be specified when columns are not provided.");
                 }
                 headers = clickHouseService.listColumns(colReq);
                 if (headers.isEmpty()) {
                    throw new SQLException("Cannot determine columns for table " + request.chTable());
                 }
                 log.info("Using fetched columns for header: {}", headers);
            } else {
                 log.info("Using specified columns for header: {}", headers);
            }
            csvWriter.writeNext(headers.toArray(String[]::new), false);

            java.util.function.Consumer<List<Object>> rowConsumer = row -> {
                String[] stringArray = row.stream()
                                          .map(obj -> obj == null ? "" : String.valueOf(obj))
                                          .toArray(String[]::new);
                csvWriter.writeNext(stringArray, false);
                recordsProcessed.incrementAndGet();
            };

            // Ensure chTable is provided for streaming
            if (request.chTable() == null || request.chTable().isBlank()) {
                throw new IllegalArgumentException("ClickHouse source table must be specified for streaming.");
            }

            clickHouseService.streamTableData(chConn, request.chTable(), headers, rowConsumer);

            log.info("CH -> FF Ingestion complete. Wrote {} records to {}", recordsProcessed.get(), request.ffPath());
            return new IngestionResult(recordsProcessed.get());

        } catch (SQLException | IOException e) {
            log.error("Error during CH -> FF ingestion: {}", e.getMessage(), e);
            throw e;
        } 
    }
}