package com.task05.request;

import java.util.Map;

public class EventData {
    private int id;
    private int principalId;
    private String createdAt;
    private Map<String, String>  body;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(int principalId) {
        this.principalId = principalId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public void setBody(Map<String, String> body) {
        this.body = body;
    }
}
