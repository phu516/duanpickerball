package com.pickleball.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String username;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private String role;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private LocalDateTime createdAt;
    private String fullName;
    private String phone;
    private String email;
    @Column(nullable = false) private boolean emailVerified = true;
    private Long eloPlayerId;

    protected UserAccount() {}

    public UserAccount(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount(String username, String passwordHash, String fullName, String phone, String email) {
        this(username, passwordHash, "PLAYER");
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.enabled = false;
        this.emailVerified = false;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public Long getEloPlayerId() { return eloPlayerId; }
}
