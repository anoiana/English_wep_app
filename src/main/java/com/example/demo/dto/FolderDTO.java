package com.example.demo.dto;


public class FolderDTO {
    public record FolderCreationDTO(String name, Long userId) {}
    public record FolderUpdateDTO(String newName) {}
}