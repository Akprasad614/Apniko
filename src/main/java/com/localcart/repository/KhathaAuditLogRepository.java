package com.localcart.repository;
import com.localcart.entity.KhathaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
public interface KhathaAuditLogRepository extends JpaRepository<KhathaAuditLog,Long> {\n    java.util.List<KhathaAuditLog> findAllByOrderByCreatedAtDesc();\n}
