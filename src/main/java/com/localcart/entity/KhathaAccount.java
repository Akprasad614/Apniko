package com.localcart.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="khatha_accounts",
    uniqueConstraints=@UniqueConstraint(name="uk_khatha_shop_customer", columnNames={"shop_id","customer_id"}),
    indexes={@Index(name="idx_khatha_customer", columnList="customer_id"),
             @Index(name="idx_khatha_shop_status", columnList="shop_id,status")})
public class KhathaAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="shop_id", nullable=false) private Shop shop;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="customer_id", nullable=false) private User customer;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal creditLimit=BigDecimal.ZERO;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal outstandingBalance=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private KhathaAccountStatus status=KhathaAccountStatus.OFFERED;
    @Column(nullable=false) private int repaymentDays=15;
    @Column(nullable=false) private int termsVersion=1;
    @Column(nullable=false) private int acceptedTermsVersion=0;
    private LocalDate dueDate;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version private Long version;

    public KhathaAccount() {}
    @PrePersist protected void onCreate() {
        LocalDateTime now=LocalDateTime.now(); createdAt=now; updatedAt=now;
        if(outstandingBalance==null) outstandingBalance=BigDecimal.ZERO;
    }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
    public Long getId(){return id;}
    public Shop getShop(){return shop;} public void setShop(Shop v){shop=v;}
    public User getCustomer(){return customer;} public void setCustomer(User v){customer=v;}
    public BigDecimal getCreditLimit(){return creditLimit;} public void setCreditLimit(BigDecimal v){creditLimit=v;}
    public BigDecimal getOutstandingBalance(){return outstandingBalance;} public void setOutstandingBalance(BigDecimal v){outstandingBalance=v;}
    public KhathaAccountStatus getStatus(){return status;} public void setStatus(KhathaAccountStatus v){status=v;}
    public int getRepaymentDays(){return repaymentDays;} public void setRepaymentDays(int v){repaymentDays=v;}
    public int getTermsVersion(){return termsVersion;} public void setTermsVersion(int v){termsVersion=v;}
    public int getAcceptedTermsVersion(){return acceptedTermsVersion;} public void setAcceptedTermsVersion(int v){acceptedTermsVersion=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
    public LocalDateTime getAcceptedAt(){return acceptedAt;} public void setAcceptedAt(LocalDateTime v){acceptedAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
