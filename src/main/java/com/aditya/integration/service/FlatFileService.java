package com.aditya.integration.service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public interface FlatFileService {

    /**
     * Creates a CSV writer for the specified path.
     *
     * @param filePath Output file path.
     * @param delimiter CSV delimiter character.
     * @return Writer instance for CSV data.
     * @throws IOException If file cannot be opened for writing.
     */
    Writer createCsvWriter(String filePath, char delimiter) throws IOException;

    /**
     * Writes the header row to the CSV.
     *
     * @param writer Writer instance.
     * @param headers List of header strings.
     * @throws IOException On I/O error.
     */
    void writeCsvHeader(Writer writer, List<String> headers) throws IOException;

    /**
     * Writes a single data row to the CSV.
     * Assumes data conversion to String happens before calling.
     *
     * @param writer Writer instance.
     * @param row List of objects representing row data.
     * @throws IOException On I/O error.
     */
    void writeCsvRow(Writer writer, List<Object> row) throws IOException;

} 