package com.example.app.model;

import java.util.List;
import java.util.Map;

public class AppDataModel {
    private List<String> headers;
    private List<Map<String, String>> rawData;

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<Map<String, String>> getRawData() {
        return rawData;
    }

    public void setRawData(List<Map<String, String>> rawData) {
        this.rawData = rawData;
    }
}
