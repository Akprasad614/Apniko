package com.localcart.dto;

import com.localcart.entity.Role;

public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private Role role;
    private boolean active;

    public UserResponse() {
    }

    public UserResponse(Long id, String name, String email, Role role, boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}