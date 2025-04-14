package com.aditya.integration.service;

import com.aditya.integration.model.IngestionRequest;
import com.aditya.integration.model.IngestionResult;

public interface IngestionService {

    /**
     * Performs data ingestion based on the request.
     *
     * @param request The ingestion configuration.
     * @return Result containing outcome (e.g., records processed).
     * @throws Exception on ingestion errors.
     */
    IngestionResult performIngestion(IngestionRequest request) throws Exception;
} 