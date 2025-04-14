package com.aditya.integration.model;

public record ColumnRequest(
    String host,
    Integer port,
    String database,
    String user,
    String token,
    String table
) {}