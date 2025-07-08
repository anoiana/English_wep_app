package com.example.demo.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "users") // Đặt tên bảng là "users" để tránh trùng từ khóa "user" của SQL
@Getter // Tự động tạo tất cả các phương thức getter
@Setter // Tự động tạo tất cả các phương thức setter
@NoArgsConstructor // Tự động tạo constructor không tham số (bắt buộc cho JPA)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // Đảm bảo username không rỗng và là duy nhất
    private String username;

    @Column(nullable = false) // Đảm bảo password không rỗng
    private String password;

    // fetch = FetchType.LAZY là một best practice để cải thiện hiệu năng
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Folder> folders;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Folder> getFolders() {
        return folders;
    }

    public void setFolders(List<Folder> folders) {
        this.folders = folders;
    }
}