package com.pickleball.court.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "courts")
public class Court {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String surface;
    @Column(nullable = false) private boolean indoor;
    private String description;
    private String imageUrl;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private boolean locked;

    protected Court() {}

    public Court(String code, String name, String surface, boolean indoor, String description) {
        this.code = code;
        this.name = name;
        this.surface = surface;
        this.indoor = indoor;
        this.description = description;
        this.status = "ACTIVE";
        this.locked = false;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSurface() { return surface; }
    public boolean isIndoor() { return indoor; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public boolean isLocked() { return locked; }

    public void update(String name, String surface, boolean indoor, String description, String status) {
        this.name = name;
        this.surface = surface;
        this.indoor = indoor;
        this.description = description;
        this.status = status;
    }

    public void setLocked(boolean locked) { this.locked = locked; }
}
