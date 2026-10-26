package com.shareroom.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="members")
public class Member {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Room room;
    @Column(nullable=false, length=80) private String name;
    @Column(nullable=false) private Instant joinedAt=Instant.now();
    @Column(nullable=false) private Instant lastSeenAt=Instant.now();
    @Column(nullable=false) private boolean active=true;
    public Member() {}
    public Member(Room room,String name){this.room=room;this.name=name;}
    public UUID getId(){return id;} public Room getRoom(){return room;} public String getName(){return name;}
    public Instant getJoinedAt(){return joinedAt;} public Instant getLastSeenAt(){return lastSeenAt;}
    public boolean isActive(){return active;} public void deactivate(){active=false;}
    public void touch(){lastSeenAt=Instant.now();}
}
