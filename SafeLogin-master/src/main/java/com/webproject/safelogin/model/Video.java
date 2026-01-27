package com.webproject.safelogin.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class Video {
    @Id
    @GeneratedValue
    private Integer id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String url;

    @OneToMany(mappedBy = "video")
    private List<Comment> comments;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    public Video() {
    }

    public Video(Integer id, String title, String url, User owner) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.owner = owner;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "Video{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", owner=" + (owner != null ? owner.getNick() : "null") +
                '}';
    }
}
