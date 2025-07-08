// file: src/main/java/com/example/demo/repositories/DefinitionRepository.java
package com.example.demo.repositories;

import com.example.demo.entities.Definition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DefinitionRepository extends JpaRepository<Definition, Long> {

    // Phương thức để xóa tất cả các Definition thuộc về một danh sách các Meaning ID
    @Modifying // Bắt buộc phải có khi thực hiện câu lệnh UPDATE hoặc DELETE
    @Query("DELETE FROM Definition d WHERE d.meaning.id IN :meaningIds")
    void deleteByMeaningIds(@Param("meaningIds") List<Long> meaningIds);
}