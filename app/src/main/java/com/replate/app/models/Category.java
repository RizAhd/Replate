package com.replate.app.models;

/**
 * Model class for Food Categories
 */
public class Category {
    private String categoryId;
    private String name;
    private int icon;
    private String color;

    public Category() {}

    public Category(String categoryId, String name, int icon) {
        this.categoryId = categoryId;
        this.name = name;
        this.icon = icon;
    }

    // Getters
    public String getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public int getIcon() { return icon; }
    public String getColor() { return color; }

    // Setters
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public void setName(String name) { this.name = name; }
    public void setIcon(int icon) { this.icon = icon; }
    public void setColor(String color) { this.color = color; }
}

