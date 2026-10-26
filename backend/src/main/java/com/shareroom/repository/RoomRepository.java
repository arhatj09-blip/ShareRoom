package com.shareroom.repository;
import com.shareroom.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoomRepository extends JpaRepository<Room,UUID> { Optional<Room> findByCode(String code); List<Room> findByClosedFalse(); }
