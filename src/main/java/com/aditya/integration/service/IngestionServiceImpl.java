package com.aditya.integration.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aditya.integration.model.ClickHouseConnectionRequest;
import com.aditya.integration.model.ColumnRequest;
import com.aditya.integration.model.IngestionRequest;
import com.aditya.integration.model.IngestionResult;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

@Service
public class IngestionServiceImpl implements IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceImpl.class);

    private final ClickHouseService clickHouseService;
    private final FlatFileService flatFileService;

    @Autowired
    public IngestionServiceImpl(ClickHouseService clickHouseService, FlatFileService flatFileService) {
        this.clickHouseService = clickHouseService;
        this.flatFileService = flatFileService;
    }

    @Override
    public IngestionResult performIngestion(IngestionRequest request) throws Exception {
        log.info("Performing ingestion: Direction={}, CH Table={}, FF Path={}",
                 request.direction(), request.chTable(), request.ffPath());

        if ("ch_to_ff".equalsIgnoreCase(request.direction())) {
            return ingestClickHouseToFlatFile(request);
        } else if ("ff_to_ch".equalsIgnoreCase(request.direction())) {
            log.error("FF -> CH ingestion not implemented.");
            throw new UnsupportedOperationException("Flat File to ClickHouse ingestion not yet implemented.");
        } else {
            throw new IllegalArgumentException("Invalid ingestion direction: " + request.direction());
        }
    }

    private IngestionResult ingestClickHouseToFlatFile(IngestionRequest request) throws SQLException, IOException {
        ClickHouseConnectionRequest chConn = new ClickHouseConnectionRequest(
            request.chHost(), request.chPort(), request.chDatabase(), request.chUser(), request.chToken()
        );

        char delimiter = request.ffDelimiter() != null && request.ffDelimiter().length() == 1 ?
                         request.ffDelimiter().charAt(0) : ICSVWriter.DEFAULT_SEPARATOR;

        AtomicLong recordsProcessed = new AtomicLong(0);

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

            clickHouseService.streamTableData(chConn, request.chTable(), headers, rowConsumer);

            log.info("CH -> FF Ingestion complete. Wrote {} records to {}", recordsProcessed.get(), request.ffPath());
            return new IngestionResult(recordsProcessed.get());

        } catch (SQLException | IOException e) {
            log.error("Error during CH -> FF ingestion: {}", e.getMessage(), e);
            throw e;
        } 
    }

}