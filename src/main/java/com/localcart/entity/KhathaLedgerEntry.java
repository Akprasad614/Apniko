package com.localcart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="khatha_ledger_entries",
    uniqueConstraints=@UniqueConstraint(name="uk_khatha_ledger_source", columnNames={"account_id","source_type","source_id"}),
    indexes=@Index(name="idx_khatha_ledger_account_created", columnList="account_id,created_at"))
public class KhathaLedgerEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="account_id", nullable=false) private KhathaAccount account;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private KhathaLedgerEntryType entryType;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal outstandingAfter;
    @Column(precision=12, scale=2) private BigDecimal creditBalanceAfter=BigDecimal.ZERO;
    @Column(precision=12, scale=2) private BigDecimal creditBalanceChange=BigDecimal.ZERO;
    @Column(precision=12, scale=2) private BigDecimal outstandingChange=BigDecimal.ZERO;
    @Column(name="source_type", nullable=false, length=32) private String sourceType;
    @Column(name="source_id", nullable=false, length=80) private String sourceId;
    @Column(nullable=false, length=500) private String description;
    @Column(length=120) private String actorName;
    @Column(length=120) private String actorEmail;
    @Column(length=24) private String actorRole;
    private LocalDate dueDate;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;

    public KhathaLedgerEntry() {}
    @PrePersist protected void onCreate(){if(createdAt==null) createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public KhathaAccount getAccount(){return account;} public void setAccount(KhathaAccount v){account=v;}
    public KhathaLedgerEntryType getEntryType(){return entryType;} public void setEntryType(KhathaLedgerEntryType v){entryType=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public BigDecimal getOutstandingAfter(){return outstandingAfter;} public void setOutstandingAfter(BigDecimal v){outstandingAfter=v;}
    public BigDecimal getCreditBalanceAfter(){return creditBalanceAfter;} public void setCreditBalanceAfter(BigDecimal v){creditBalanceAfter=v;}
    public BigDecimal getCreditBalanceChange(){return creditBalanceChange;} public void setCreditBalanceChange(BigDecimal v){creditBalanceChange=v;}
    public BigDecimal getOutstandingChange(){return outstandingChange;} public void setOutstandingChange(BigDecimal v){outstandingChange=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getSourceId(){return sourceId;} public void setSourceId(String v){sourceId=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getActorName(){return actorName;} public void setActorName(String v){actorName=v;}
    public String getActorEmail(){return actorEmail;} public void setActorEmail(String v){actorEmail=v;}
    public String getActorRole(){return actorRole;} public void setActorRole(String v){actorRole=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
