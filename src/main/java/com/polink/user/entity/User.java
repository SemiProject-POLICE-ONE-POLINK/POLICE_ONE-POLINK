package com.polink.user.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tbl_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_name", length = 25, nullable = false, unique = true)
    private String userName;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "name", length = 25, nullable = false)
    private String name;

    @Column(name = "phone", length = 25, nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 25, nullable = false)
    private UserRole role;

    protected User() {
    }

    public User(String userName, String encodedPassword,
                String name, String phone) {
        this.userName = userName;
        this.password = encodedPassword;
        this.name = name;
        this.phone = phone;
        this.role = UserRole.CITIZEN;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }
}