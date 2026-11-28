package com.localcart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="khatha_repayments",
    uniqueConstraints=@UniqueConstraint(name="uk_khatha_repayment_key", columnNames="idempotency_key"),
    indexes=@Index(name="idx_khatha_repayment_account_status", columnList="account_id,status"))
public class KhathaRepayment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="account_id", nullable=false) private KhathaAccount account;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private KhathaRepaymentChannel channel;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private KhathaRepaymentStatus status=KhathaRepaymentStatus.PENDING;
    @Column(name="idempotency_key", nullable=false, unique=true, length=80) private String idempotencyKey;
    @Column(length=120) private String reference;
    @Column(length=500) private String note;
    @Column(length=120) private String actedByName;
    @Column(length=500) private String decisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    public KhathaRepayment() {}
    @PrePersist protected void onCreate(){if(createdAt==null) createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public KhathaAccount getAccount(){return account;} public void setAccount(KhathaAccount v){account=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public KhathaRepaymentChannel getChannel(){return channel;} public void setChannel(KhathaRepaymentChannel v){channel=v;}
    public KhathaRepaymentStatus getStatus(){return status;} public void setStatus(KhathaRepaymentStatus v){status=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getReference(){return reference;} public void setReference(String v){reference=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public String getActedByName(){return actedByName;} public void setActedByName(String v){actedByName=v;}
    public String getDecisionReason(){return decisionReason;} public void setDecisionReason(String v){decisionReason=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(LocalDateTime v){confirmedAt=v;}
}
