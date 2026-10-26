package com.shareroom.controller;

import com.shareroom.dto.Dtos.*;
import com.shareroom.entity.*;
import com.shareroom.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/rooms")
public class RoomController {
 private final RoomService service;
 public RoomController(RoomService s){service=s;}
 @PostMapping public RoomResponse create(@Valid @RequestBody CreateRoomRequest request){return service.create(request);}
 @PostMapping("/{roomId}/join") public RoomResponse join(@PathVariable String roomId,@Valid @RequestBody JoinRequest request){return service.join(roomId,request);}
 @GetMapping("/{roomId}") public Map<String,Object> details(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID memberId){
   Room r=service.getRoom(roomId); service.member(r,memberId); return Map.of("roomId",r.getCode(),"roomName",r.getName() == null ? "" : r.getName(),"closed",r.isClosed(),"members",service.memberResponses(r));
 }
 @GetMapping("/{roomId}/members") public List<MemberResponse> members(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID memberId){Room r=service.getRoom(roomId);service.member(r,memberId);return service.memberResponses(r);}
 @PostMapping("/{roomId}/leave") @ResponseStatus(HttpStatus.NO_CONTENT) public void leave(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID id){service.leave(service.getRoom(roomId),id);}
 @PostMapping("/{roomId}/close") @ResponseStatus(HttpStatus.NO_CONTENT) public void close(@PathVariable String roomId,@RequestHeader("X-Member-Id") UUID id){service.close(service.getRoom(roomId),id);}
}
