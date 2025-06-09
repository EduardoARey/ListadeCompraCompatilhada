package com.example.listacomprascompartilhada;

public class ShoppingItem {
    private String id;
    private String name;
    private String quantity;
    private boolean completed;
    private String addedBy;
    private long createdAt;
    private long lastModified;

    public ShoppingItem() {
    }

    public ShoppingItem(String name, String quantity, boolean completed, String addedBy, long createdAt) {
        this.name = name;
        this.quantity = quantity;
        this.completed = completed;
        this.addedBy = addedBy;
        this.createdAt = createdAt;
        this.lastModified = createdAt;
    }

    // Getters e Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}