package com.shareroom.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="transfers")
public class Transfer {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Room room;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Member sender;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) private Member recipient;
    @Column(nullable=false) private String originalName;
    @Column(nullable=false) private String storedPath;
    @Column(nullable=false) private long size;
    @Column(nullable=false, length=64) private String sha256;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant expiresAt;
    public Transfer() {}
    public Transfer(Room r,Member s,Member t,String n,String p,long z,String hash){
        room=r;sender=s;recipient=t;originalName=n;storedPath=p;size=z;sha256=hash;expiresAt=Instant.now().plusSeconds(86400);
    }
    public UUID getId(){return id;} public Room getRoom(){return room;} public Member getSender(){return sender;}
    public Member getRecipient(){return recipient;} public String getOriginalName(){return originalName;}
    public String getStoredPath(){return storedPath;} public long getSize(){return size;} public String getSha256(){return sha256;}
    public Instant getCreatedAt(){return createdAt;} public Instant getExpiresAt(){return expiresAt;}
}
