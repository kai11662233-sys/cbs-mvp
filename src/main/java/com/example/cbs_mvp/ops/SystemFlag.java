package com.example.cbs_mvp.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_flags")
@Getter
@Setter
@NoArgsConstructor
public class SystemFlag {

    @Id
    @Column(name = "key", length = 50)
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SystemFlag(String key) {
        this.key = key;
    }
}
