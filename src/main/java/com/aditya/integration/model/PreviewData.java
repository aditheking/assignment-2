package com.aditya.integration.model;

import java.util.List;

public record PreviewData(
    List<String> headers,
    List<List<Object>> rows
) {}