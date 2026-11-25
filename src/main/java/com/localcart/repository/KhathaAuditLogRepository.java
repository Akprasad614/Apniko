package com.localcart.repository;
import com.localcart.entity.KhathaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface KhathaAuditLogRepository extends JpaRepository<KhathaAuditLog,Long> {
    List<KhathaAuditLog> findAllByOrderByCreatedAtDesc();
}
