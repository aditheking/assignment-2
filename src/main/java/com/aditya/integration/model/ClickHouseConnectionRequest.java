package com.aditya.integration.model;

public record ClickHouseConnectionRequest(
    String host,
    Integer port,
    String database,
    String user,
    String token
) {}
