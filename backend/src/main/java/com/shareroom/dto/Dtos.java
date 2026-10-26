package com.shareroom.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}
    public record CreateRoomRequest(@NotBlank @Size(max=80) String name, @Size(max=100) String roomName) {}
    public record JoinRequest(@NotBlank @Size(max=80) String name) {}
    public record RoomResponse(String roomId, String roomName, UUID roomInternalId, UUID memberId, String memberName) {}
    public record MemberResponse(UUID id,String name,Instant joinedAt) {}
    public record TransferResponse(UUID id,String fileName,long size,String sender, String recipient,Instant createdAt,Instant expiresAt,String checksumStatus) {}
    public record ActivityResponse(UUID id,String action,String fileName,String member,Instant at) {}
}
