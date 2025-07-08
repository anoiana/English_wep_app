package com.example.demo.dto;


// Đặt các DTO ở đây dưới dạng public record
public class FolderDTO {

    // DTO để nhận dữ liệu khi tạo folder
    public record FolderCreationDTO(String name, Long userId) {}

    // DTO để nhận dữ liệu khi cập nhật tên folder
    public record FolderUpdateDTO(String newName) {}

}