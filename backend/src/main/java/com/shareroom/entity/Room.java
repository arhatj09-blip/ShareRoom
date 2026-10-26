package com.shareroom.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="rooms", indexes=@Index(name="idx_room_code", columnList="code", unique=true))
public class Room {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, unique=true, length=9) private String code;
    @Column(length=100) private String name;
    @Column private UUID hostMemberId;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant lastActivityAt=Instant.now();
    @Column(nullable=false) private boolean closed;
    public Room() {}
    public Room(String code){this(code, null);}
    public Room(String code, String name){this.code=code;this.name=name;}
    public UUID getId(){return id;} public String getCode(){return code;}
    public String getName(){return name;} public void setHostMemberId(UUID id){hostMemberId=id;}
    public boolean isHost(UUID id){return id != null && id.equals(hostMemberId);}
    public Instant getCreatedAt(){return createdAt;} public Instant getLastActivityAt(){return lastActivityAt;}
    public boolean isClosed(){return closed;} public void close(){closed=true;}
    public void touch(){lastActivityAt=Instant.now();}
}
