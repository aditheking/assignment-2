package com.aditya.integration.model;

import java.util.List;

public record IngestionRequest(
    String direction,

    String chHost,
    Integer chPort,
    String chDatabase,
    String chUser,
    String chToken,
    String chTable,

    String ffPath,
    String ffDelimiter,

    List<String> columns
) {}