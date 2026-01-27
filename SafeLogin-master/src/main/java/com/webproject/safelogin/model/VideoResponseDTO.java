package com.webproject.safelogin.model;

public class VideoResponseDTO {
    private Integer id;
    private String title;
    private String url;
    private Integer ownerId;
    private String ownerNick;

    public VideoResponseDTO(Integer id, String title, String url, Integer ownerId, String ownerNick) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.ownerId = ownerId;
        this.ownerNick = ownerNick;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public String getOwnerNick() {
        return ownerNick;
    }
}
