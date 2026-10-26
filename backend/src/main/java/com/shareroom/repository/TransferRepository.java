package com.shareroom.repository;
import com.shareroom.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant; import java.util.*;
public interface TransferRepository extends JpaRepository<Transfer,UUID> {
 List<Transfer> findByRecipientOrderByCreatedAtDesc(Member member); List<Transfer> findByRoomOrderByCreatedAtDesc(Room room);
 List<Transfer> findByExpiresAtBefore(Instant time);
}
