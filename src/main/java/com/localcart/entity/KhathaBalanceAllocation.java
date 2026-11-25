package com.localcart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="khatha_balance_allocations",
    uniqueConstraints=@UniqueConstraint(name="uk_khatha_debit_credit_allocation",
        columnNames={"debit_entry_id","credit_entry_id","allocation_type"}),
    indexes={@Index(name="idx_khatha_allocation_debit",columnList="debit_entry_id"),
             @Index(name="idx_khatha_allocation_credit",columnList="credit_entry_id")})
public class KhathaBalanceAllocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="account_id",nullable=false)
    private KhathaAccount account;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="debit_entry_id",nullable=false)
    private KhathaLedgerEntry debitEntry;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="credit_entry_id",nullable=false)
    private KhathaLedgerEntry creditEntry;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(name="allocation_type",nullable=false,length=16)
    private KhathaAllocationType allocationType;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;

    public KhathaBalanceAllocation() {}
    @PrePersist protected void onCreate(){if(createdAt==null)createdAt=LocalDateTime.now();}
    public Long getId(){return id;}
    public KhathaAccount getAccount(){return account;} public void setAccount(KhathaAccount v){account=v;}
    public KhathaLedgerEntry getDebitEntry(){return debitEntry;} public void setDebitEntry(KhathaLedgerEntry v){debitEntry=v;}
    public KhathaLedgerEntry getCreditEntry(){return creditEntry;} public void setCreditEntry(KhathaLedgerEntry v){creditEntry=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public KhathaAllocationType getAllocationType(){return allocationType;} public void setAllocationType(KhathaAllocationType v){allocationType=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
