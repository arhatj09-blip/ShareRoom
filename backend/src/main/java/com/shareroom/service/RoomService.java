package com.shareroom.service;

import com.shareroom.dto.Dtos.*;
import com.shareroom.entity.*;
import com.shareroom.exception.ApiException;
import com.shareroom.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoomService {
 private static final Set<String> ALLOWED=Set.of("pdf","txt","csv","jpg","jpeg","png","gif","webp","doc","docx","xls","xlsx","ppt","pptx","zip","7z","tar","gz","mp3","mp4");
 private static final Set<String> BLOCKED=Set.of("exe","bat","cmd","msi","jar","com","scr","sh","ps1","vbs","js","dll");
 private final RoomRepository rooms; private final MemberRepository members; private final TransferRepository transfers; private final Path uploadDir;
 public RoomService(RoomRepository r,MemberRepository m,TransferRepository t,org.springframework.core.env.Environment env) {
   rooms=r;members=m;transfers=t;uploadDir=Path.of(env.getProperty("shareroom.upload-dir","uploads")).toAbsolutePath().normalize();
   try { Files.createDirectories(uploadDir); } catch(IOException e){ throw new IllegalStateException("Cannot create upload directory",e); }
 }
 @Transactional public RoomResponse create(CreateRoomRequest req) {
   String code; do {code=String.format("%s-%04d",new String[]{"BLUE","LIME","GOLD","PINK","TEAL","MINT"}[new Random().nextInt(6)],new Random().nextInt(10000));} while(rooms.findByCode(code).isPresent());
   Room room=rooms.save(new Room(code, req.roomName() == null || req.roomName().isBlank() ? null : req.roomName().trim()));
   Member m=members.save(new Member(room,cleanName(req.name()))); room.setHostMemberId(m.getId()); rooms.save(room);
   return response(room,m);
 }
 @Transactional public RoomResponse join(String code,JoinRequest req) {
   Room room=getRoom(code); if(room.isClosed()) throw new ApiException(HttpStatus.GONE,"Room is closed");
   String name=cleanName(req.name()); if(members.existsByRoomAndNameIgnoreCaseAndActiveTrue(room,name)) throw new ApiException(HttpStatus.CONFLICT,"Name is already in use");
   Member m=members.save(new Member(room,name)); room.touch(); return response(room,m);
 }
 @Transactional public Room getRoom(String code){return rooms.findByCode(code.toUpperCase(Locale.ROOT)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Room not found"));}
 @Transactional  public Member member(Room room,UUID id){Member m=members.findById(id).filter(x->x.isActive()&&x.getRoom().getId().equals(room.getId())).orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"Membership required")); m.touch(); room.touch(); return m;}
 public RoomResponse response(Room r,Member m){return new RoomResponse(r.getCode(),r.getName(),r.getId(),m.getId(),m.getName());}
 public List<MemberResponse> memberResponses(Room r){return members.findByRoomAndActiveTrue(r).stream().map(m->new MemberResponse(m.getId(),m.getName(),m.getJoinedAt())).toList();}
 @Transactional public void leave(Room r,UUID id){Member m=member(r,id); m.deactivate(); r.touch();}
 @Transactional public void close(Room r,UUID id){member(r,id); if(!r.isHost(id)) throw new ApiException(HttpStatus.FORBIDDEN,"Only the host can close this room"); r.close();}
 @Transactional public Transfer saveUpload(Room r,UUID senderId,UUID recipientId,MultipartFile file) {
   Member sender=member(r,senderId), recipient=member(r,recipientId); if(file.isEmpty()||file.getSize()>25L*1024*1024) throw new ApiException(HttpStatus.BAD_REQUEST,"File must be between 1 byte and 25 MB");
   String original=safeName(file.getOriginalFilename()); String ext=extension(original); if(!ALLOWED.contains(ext)||BLOCKED.contains(ext)) throw new ApiException(HttpStatus.BAD_REQUEST,"File type is not allowed");
   String stored=UUID.randomUUID()+"_"+original; Path target=uploadDir.resolve(stored).normalize(); if(!target.startsWith(uploadDir)) throw new ApiException(HttpStatus.BAD_REQUEST,"Invalid filename");
   try { file.transferTo(target); String hash=sha(target); return transfers.save(new Transfer(r,sender,recipient,original,stored,file.getSize(),hash)); }
   catch(IOException e){try{Files.deleteIfExists(target);}catch(IOException ignored){} throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not store file");}
 }
 @Transactional(readOnly=true) public List<TransferResponse> inbox(Member m){return transfers.findByRecipientOrderByCreatedAtDesc(m).stream().map(this::transfer).toList();}
 @Transactional(readOnly=true) public List<TransferResponse> activity(Room r){return transfers.findByRoomOrderByCreatedAtDesc(r).stream().map(this::transfer).toList();}
 private TransferResponse transfer(Transfer t){return new TransferResponse(t.getId(),t.getOriginalName(),t.getSize(),t.getSender().getName(),t.getRecipient().getName(),t.getCreatedAt(),t.getExpiresAt(),"Verified");}
 @Transactional public FileSystemResource download(Room r,UUID memberId,UUID transferId){Member m=member(r,memberId); Transfer t=transfers.findById(transferId).filter(x->x.getRoom().getId().equals(r.getId())&&x.getRecipient().getId().equals(m.getId())).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Transfer not found")); if(t.getExpiresAt().isBefore(Instant.now())) throw new ApiException(HttpStatus.GONE,"File has expired"); Path p=uploadDir.resolve(t.getStoredPath()).normalize(); try {if(!Files.exists(p)||!sha(p).equals(t.getSha256())) throw new ApiException(HttpStatus.GONE,"File is unavailable"); return new FileSystemResource(p);} catch(IOException e){throw new ApiException(HttpStatus.GONE,"File is unavailable");}}
 @Scheduled(cron="0 */10 * * * *") @Transactional public void cleanup(){Instant now=Instant.now(); for(Transfer t:transfers.findByExpiresAtBefore(now)){try{Files.deleteIfExists(uploadDir.resolve(t.getStoredPath()).normalize());}catch(IOException ignored){} transfers.delete(t);} for(Room r:rooms.findByClosedFalse()) if(r.getLastActivityAt().isBefore(now.minusSeconds(1800))||members.findByRoomAndActiveTrue(r).isEmpty()){r.close();}}
 private String cleanName(String n){String x=n.trim(); if(x.isBlank()||x.length()>80) throw new ApiException(HttpStatus.BAD_REQUEST,"Name is invalid"); return x.replaceAll("[\\r\\n]","");}
 private String safeName(String n){if(n==null||n.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"Filename is required"); String x=Paths.get(n).getFileName().toString().replaceAll("[^a-zA-Z0-9._ -]","_"); if(x.isBlank()||x.length()>180) throw new ApiException(HttpStatus.BAD_REQUEST,"Filename is invalid"); return x;}
 private String extension(String n){int i=n.lastIndexOf('.');return i<0?"":n.substring(i+1).toLowerCase(Locale.ROOT);}
 private String sha(Path p)throws IOException{try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[8192];int z;while((z=in.read(b))!=-1)d.update(b,0,z);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b));return s.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
