package com.task06.dto;

import java.util.Map;

public class Audit {
    private String id;
    private String itemKey;
    private String modificationTime;
    private Map<String, String> newValue;
    private String oldValue;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(String modificationTime) {
        this.modificationTime = modificationTime;
    }

    public Map<String, String> getNewValue() {
        return newValue;
    }

    public void setNewValue(Map<String, String> newValue) {
        this.newValue = newValue;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }


}
