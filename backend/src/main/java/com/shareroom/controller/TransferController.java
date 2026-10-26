package com.shareroom.controller;

import com.shareroom.dto.Dtos.*;
import com.shareroom.entity.*;
import com.shareroom.service.RoomService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/rooms/{roomId}")
public class TransferController {
 private final RoomService service;
 public TransferController(RoomService s){service=s;}
 @PostMapping(value="/transfers",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public TransferResponse upload(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID sender,
   @RequestParam UUID recipientId,@RequestPart("file") MultipartFile file){Transfer t=service.saveUpload(service.getRoom(roomId),sender,recipientId,file);return new TransferResponse(t.getId(),t.getOriginalName(),t.getSize(),t.getSender().getName(),t.getRecipient().getName(),t.getCreatedAt(),t.getExpiresAt(),"Verified");}
 @GetMapping("/inbox") public List<TransferResponse> inbox(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID id){Room r=service.getRoom(roomId);return service.inbox(service.member(r,id));}
 @GetMapping("/activity") public List<TransferResponse> activity(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID id){Room r=service.getRoom(roomId);service.member(r,id);return service.activity(r);}
 @GetMapping("/transfers/{transferId}/download")
 public ResponseEntity<FileSystemResource> download(@PathVariable String roomId,@PathVariable UUID transferId,@RequestHeader("X-Member-Id") UUID id){FileSystemResource f=service.download(service.getRoom(roomId),id,transferId);return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+f.getFilename()+"\"").body(f);}
}
