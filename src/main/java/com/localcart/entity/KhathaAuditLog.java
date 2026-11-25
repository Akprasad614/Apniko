package com.localcart.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="khatha_audit_logs", indexes=@Index(name="idx_khatha_audit_account_created", columnList="account_id,created_at"))
public class KhathaAuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="account_id", nullable=false) private Long accountId;
    @Column(nullable=false, length=40) private String action;
    @Column(nullable=false, length=500) private String reason;
    @Column(nullable=false) private Long actorUserId;
    @Column(nullable=false, length=120) private String actorName;
    @Column(nullable=false, length=120) private String actorEmail;
    @Column(nullable=false, length=24) private String actorRole;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    public KhathaAuditLog() {}
    @PrePersist protected void onCreate(){if(createdAt==null) createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public Long getAccountId(){return accountId;}
    public String getAction(){return action;}
    public String getReason(){return reason;}
    public Long getActorUserId(){return actorUserId;}
    public String getActorName(){return actorName;}
    public String getActorEmail(){return actorEmail;}
    public String getActorRole(){return actorRole;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setAccountId(Long v){accountId=v;}
    public void setAction(String v){action=v;}
    public void setReason(String v){reason=v;}
    public void setActorUserId(Long v){actorUserId=v;}
    public void setActorName(String v){actorName=v;}
    public void setActorEmail(String v){actorEmail=v;}
    public void setActorRole(String v){actorRole=v;}
}
