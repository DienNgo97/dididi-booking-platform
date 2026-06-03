package com.dididi.booking.integration.domain.entity;

import com.dididi.booking.integration.domain.enums.SourceType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "external_data_sources")
public class ExternalDataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType type;

    @Column(length = 200)
    private String endpoint;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public SourceType getType() { return type; }
    public void setType(SourceType type) { this.type = type; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
