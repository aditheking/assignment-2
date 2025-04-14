package com.aditya.integration.model;

import java.util.List;

public record PreviewRequest(
    String host,
    Integer port,
    String database,
    String user,
    String token,
    String table,
    List<String> columns
) {}