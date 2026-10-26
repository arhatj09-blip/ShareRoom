package com.shareroom.repository;
import com.shareroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MemberRepository extends JpaRepository<Member,UUID> { List<Member> findByRoomAndIdNotAndActiveTrue(Room room, UUID id); List<Member> findByRoomAndActiveTrue(Room room); boolean existsByRoomAndNameIgnoreCaseAndActiveTrue(Room room,String name); }
