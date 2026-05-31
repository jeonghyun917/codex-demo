package com.kingyurina.demo.menu;

public abstract class MenuRecord {

    private Long id;

    private String code;

    private String label;

    private String href;

    private int sortOrder;

    private boolean enabled = true;

    public String getLabel() {
        return label;
    }

    public String getHref() {
        return href;
    }
}
