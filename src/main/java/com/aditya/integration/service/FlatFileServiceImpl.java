package com.aditya.integration.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opencsv.CSVWriter;

@Service
public class FlatFileServiceImpl implements FlatFileService {

    private static final Logger log = LoggerFactory.getLogger(FlatFileServiceImpl.class);

    @Override
    public Writer createCsvWriter(String filePath, char delimiter) throws IOException {
        log.info("Creating CSV writer for file: {} with delimiter: '{}'", filePath, delimiter);
        try {
            Path path = Paths.get(filePath);
            if (path.getParent() != null) {
                 Files.createDirectories(path.getParent());
            }
            return new FileWriter(filePath);
        } catch (IOException e) {
            log.error("Failed to create CSV writer for file {}: {}", filePath, e.getMessage());
            throw e;
        }
    }


    @Override
    public void writeCsvHeader(Writer writer, List<String> headers) throws IOException {
        log.debug("Writing CSV header (direct): {}", headers);
        try {
            writer.write(headers.stream().collect(Collectors.joining(String.valueOf(CSVWriter.DEFAULT_SEPARATOR))) + CSVWriter.DEFAULT_LINE_END);
        } catch (IOException e) {
             log.error("Failed to write CSV header (direct): {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void writeCsvRow(Writer writer, List<Object> row) throws IOException {
        log.trace("Writing CSV row (direct): {}", row);
        try {
            String rowString = row.stream()
                                  .map(obj -> obj == null ? "" : String.valueOf(obj))
                                  .map(s -> s.contains(",") ? "\"" + s.replace("\"", "\"\"") + "\"" : s)
                                  .collect(Collectors.joining(String.valueOf(CSVWriter.DEFAULT_SEPARATOR)));
            writer.write(rowString + CSVWriter.DEFAULT_LINE_END);
        } catch (IOException e) {
             log.error("Failed to write CSV row (direct): {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Helper to write a row using an OpenCSV writer instance.
     * Handles data conversion and CSV formatting rules.
     */
    public static void writeRowToOpenCsvWriter(CSVWriter csvWriter, List<Object> row) {
        String[] stringArray = row.stream()
                                  .map(obj -> obj == null ? "" : String.valueOf(obj))
                                  .toArray(String[]::new);
        csvWriter.writeNext(stringArray, false);
    }
} 