package com.localcart.service;

import com.localcart.dto.*;
import com.localcart.entity.*;
import com.localcart.exception.BadRequestException;
import com.localcart.exception.ResourceNotFoundException;
import com.localcart.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KhathaService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final KhathaAccountRepository accounts;
    private final KhathaLedgerEntryRepository ledger;
    private final KhathaRepaymentRepository repayments;
    private final KhathaAuditLogRepository audit;
    private final ShopRepository shops;
    private final UserRepository users;
    private final OrderRepository orders;

    public KhathaService(KhathaAccountRepository accounts, KhathaLedgerEntryRepository ledger,
            KhathaRepaymentRepository repayments, KhathaAuditLogRepository audit,
            ShopRepository shops, UserRepository users, OrderRepository orders) {
        this.accounts=accounts; this.ledger=ledger; this.repayments=repayments;
        this.audit=audit; this.shops=shops; this.users=users; this.orders=orders;
    }

    @Transactional(readOnly=true)
    public boolean hasAvailableCreditForCustomer(Long shopId,String email) {
        if(shopId==null || email==null || email.isBlank()) return false;
        User customer=users.findByEmail(email).orElse(null);
        if(customer==null || customer.getRole()!=Role.CUSTOMER || !customer.isActive()) return false;
        return accounts.findByShopIdAndCustomerId(shopId,customer.getId()).map(this::isUsable).orElse(false);
    }

    @Transactional(readOnly=true)
    public List<KhathaAccountResponse> customerAccounts(String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        return accounts.findCustomerAccounts(customer.getId()).stream().map(KhathaAccountResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public Optional<KhathaAccountResponse> customerAccountForShop(Long shopId,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        return accounts.findByShopIdAndCustomerId(shopId,customer.getId()).map(KhathaAccountResponse::from);
    }

    @Transactional(readOnly=true)
    public KhathaAccountResponse customerAccount(Long id,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        KhathaAccount account=findAccount(id); requireCustomer(account,customer);
        return KhathaAccountResponse.from(account);
    }

    @Transactional(readOnly=true)
    public List<KhathaLedgerEntryResponse> customerLedger(Long id,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        KhathaAccount account=findAccount(id); requireCustomer(account,customer);
        return ledger.findByAccountIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(KhathaLedgerEntryResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaRepaymentResponse> customerRepayments(Long id,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        KhathaAccount account=findAccount(id); requireCustomer(account,customer);
        return repayments.findByAccountIdOrderByCreatedAtDesc(id).stream()
                .map(KhathaRepaymentResponse::from).toList();
    }

    @Transactional
    public KhathaAccountResponse acceptTerms(Long id,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        KhathaAccount account=lockAccount(id); requireCustomer(account,customer);
        if(account.getStatus()!=KhathaAccountStatus.OFFERED) throw new BadRequestException("This offer is not awaiting acceptance");
        if(!account.getShop().isActive()) throw new BadRequestException("This shop is not active");
        if(account.getCreditLimit().signum()<=0) throw new BadRequestException("The credit offer is invalid");
        account.setAcceptedTermsVersion(account.getTermsVersion());
        account.setAcceptedAt(LocalDateTime.now());
        account.setStatus(KhathaAccountStatus.ACTIVE);
        if(account.getOutstandingBalance().signum()>0)
            account.setDueDate(LocalDate.now().plusDays(account.getRepaymentDays()));
        accounts.save(account);
        writeAudit(account,customer,"TERMS_ACCEPTED","Customer accepted the displayed credit limit and repayment terms");
        return KhathaAccountResponse.from(account);
    }

    @Transactional
    public KhathaAccountResponse offerCredit(KhathaOfferRequest request,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        Shop shop=shops.findById(request.shopId()).orElseThrow(()->new ResourceNotFoundException("Shop not found"));
        requireShop(shop,actor);
        if(!shop.isActive()) throw new BadRequestException("An inactive shop cannot offer credit");
        User customer=users.findById(request.customerId()).orElseThrow(()->new ResourceNotFoundException("Customer not found"));
        if(customer.getRole()!=Role.CUSTOMER || !customer.isActive()) throw new BadRequestException("Select an active customer");
        BigDecimal limit=money(request.creditLimit());
        if(limit.signum()<=0 || request.repaymentDays()<1 || request.repaymentDays()>90)
            throw new BadRequestException("Enter a valid credit limit and repayment period (1–90 days)");
        if(accounts.findByShopIdAndCustomerId(shop.getId(),customer.getId()).isPresent())
            throw new BadRequestException("A Khatha account already exists for this customer and shop");
        KhathaAccount account=new KhathaAccount();
        account.setShop(shop); account.setCustomer(customer); account.setCreditLimit(limit);
        account.setOutstandingBalance(ZERO); account.setRepaymentDays(request.repaymentDays());
        account.setStatus(KhathaAccountStatus.OFFERED); account.setTermsVersion(1); account.setAcceptedTermsVersion(0);
        account=accounts.save(account);
        writeAudit(account,actor,"CREDIT_OFFERED","Credit offer created; customer acceptance is required");
        return KhathaAccountResponse.from(account);
    }

    @Transactional
    public KhathaAccountResponse updateTerms(Long id,KhathaTermsUpdateRequest request,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(id); requireShop(account.getShop(),actor);
        if(account.getStatus()==KhathaAccountStatus.CLOSED) throw new BadRequestException("A closed Khatha account cannot be reopened; create a new account after review");
        BigDecimal limit=money(request.creditLimit());
        if(limit.signum()<=0 || request.repaymentDays()<1 || request.repaymentDays()>90)
            throw new BadRequestException("Enter a valid credit limit and repayment period");
        if(limit.compareTo(account.getOutstandingBalance())<0)
            throw new BadRequestException("Credit limit cannot be lower than the outstanding balance");
        account.setCreditLimit(limit); account.setRepaymentDays(request.repaymentDays());
        account.setTermsVersion(account.getTermsVersion()+1); account.setAcceptedTermsVersion(0);
        account.setAcceptedAt(null); account.setStatus(KhathaAccountStatus.OFFERED);
        accounts.save(account);
        writeAudit(account,actor,"TERMS_UPDATED",request.reason().trim());
        return KhathaAccountResponse.from(account);
    }

    @Transactional
    public KhathaAccountResponse updateStatus(Long id,KhathaAccountStatus status,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(id); requireShop(account.getShop(),actor);
        if(status==KhathaAccountStatus.ACTIVE) {
            if(account.getAcceptedAt()==null || account.getAcceptedTermsVersion()!=account.getTermsVersion())
                throw new BadRequestException("The customer must accept the current terms before the account can be reactivated");
            if(isOverdue(account))
                throw new BadRequestException("An overdue balance must be cleared before new credit can be reactivated");
        } else if(status==KhathaAccountStatus.CLOSED) {
            if(account.getOutstandingBalance().signum()>0)
                throw new BadRequestException("An account cannot be closed while money is outstanding");
        } else if(status!=KhathaAccountStatus.PAUSED) {
            throw new BadRequestException("Only pause, reactivate or close is permitted");
        }
        account.setStatus(status); accounts.save(account);
        writeAudit(account,actor,"STATUS_"+status.name(),"Shopkeeper changed account status to "+status.name());
        return KhathaAccountResponse.from(account);
    }

    @Transactional
    public KhathaRepaymentResponse requestRepayment(Long id,KhathaRepaymentRequest request,String email) {
        User customer=requireUser(email,Role.CUSTOMER);
        KhathaAccount account=lockAccount(id); requireCustomer(account,customer);
        KhathaRepayment existing=findIdempotent(request,id);
        if(existing!=null) return KhathaRepaymentResponse.from(existing);
        validateRepayment(account,request);
        if(request.channel()==KhathaRepaymentChannel.UPI_TRANSFER && blank(request.reference()))
            throw new BadRequestException("Enter the UPI transaction reference for shopkeeper verification");
        KhathaRepayment repayment=newRepayment(account,request,KhathaRepaymentStatus.PENDING);
        return KhathaRepaymentResponse.from(repayments.save(repayment));
    }

    @Transactional
    public KhathaRepaymentResponse confirmRepayment(Long accountId,Long repaymentId,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(accountId); requireShop(account.getShop(),actor);
        KhathaRepayment repayment=repayments.findByIdForUpdate(repaymentId)
                .orElseThrow(()->new ResourceNotFoundException("Repayment request not found"));
        verifyRepaymentAccount(repayment,accountId);
        if(repayment.getStatus()==KhathaRepaymentStatus.CONFIRMED) return KhathaRepaymentResponse.from(repayment);
        if(repayment.getStatus()!=KhathaRepaymentStatus.PENDING) throw new BadRequestException("Only pending repayments can be confirmed");
        applyRepayment(account,repayment,actor,"REPAYMENT");
        repayment.setStatus(KhathaRepaymentStatus.CONFIRMED); repayment.setActedByName(actor.getName());
        repayment.setConfirmedAt(LocalDateTime.now()); repayment.setDecisionReason("Receipt verified by shopkeeper");
        repayments.save(repayment);
        writeAudit(account,actor,"REPAYMENT_CONFIRMED","Confirmed repayment #"+repaymentId);
        return KhathaRepaymentResponse.from(repayment);
    }

    @Transactional
    public KhathaRepaymentResponse rejectRepayment(Long accountId,Long repaymentId,String reason,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(accountId); requireShop(account.getShop(),actor);
        KhathaRepayment repayment=repayments.findByIdForUpdate(repaymentId)
                .orElseThrow(()->new ResourceNotFoundException("Repayment request not found"));
        verifyRepaymentAccount(repayment,accountId);
        if(repayment.getStatus()==KhathaRepaymentStatus.REJECTED) return KhathaRepaymentResponse.from(repayment);
        if(repayment.getStatus()!=KhathaRepaymentStatus.PENDING) throw new BadRequestException("A confirmed repayment cannot be rejected");
        repayment.setStatus(KhathaRepaymentStatus.REJECTED); repayment.setActedByName(actor.getName());
        repayment.setDecisionReason(blank(reason)?"Shopkeeper could not verify receipt":reason.trim());
        repayments.save(repayment);
        writeAudit(account,actor,"REPAYMENT_REJECTED","Rejected repayment #"+repaymentId+": "+repayment.getDecisionReason());
        return KhathaRepaymentResponse.from(repayment);
    }

    @Transactional
    public KhathaRepaymentResponse recordRepayment(Long id,KhathaRepaymentRequest request,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(id); requireShop(account.getShop(),actor);
        KhathaRepayment existing=findIdempotent(request,id);
        if(existing!=null) return KhathaRepaymentResponse.from(existing);
        validateRepayment(account,request);
        if(request.channel()==KhathaRepaymentChannel.UPI_TRANSFER && blank(request.reference()))
            throw new BadRequestException("Enter the verified UPI transaction reference");
        KhathaRepayment repayment=newRepayment(account,request,KhathaRepaymentStatus.CONFIRMED);
        repayment.setActedByName(actor.getName()); repayment.setConfirmedAt(LocalDateTime.now());
        repayment.setDecisionReason("Recorded by shopkeeper after receiving payment");
        repayment=repayments.save(repayment);
        applyRepayment(account,repayment,actor,"REPAYMENT");
        writeAudit(account,actor,"REPAYMENT_RECORDED","Recorded repayment #"+repayment.getId());
        return KhathaRepaymentResponse.from(repayment);
    }

    @Transactional
    public KhathaLedgerEntryResponse adjustBalance(Long id,KhathaAdjustmentRequest request,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER);
        KhathaAccount account=lockAccount(id); requireShop(account.getShop(),actor);
        BigDecimal amount=money(request.amount()), next;
        KhathaLedgerEntryType type;
        if(request.type()==KhathaAdjustmentType.CREDIT) {
            if(amount.compareTo(account.getOutstandingBalance())>0) throw new BadRequestException("A credit adjustment cannot exceed the outstanding balance");
            next=account.getOutstandingBalance().subtract(amount); type=KhathaLedgerEntryType.ADJUSTMENT_CREDIT;
        } else {
            next=account.getOutstandingBalance().add(amount);
            if(next.compareTo(account.getCreditLimit())>0) throw new BadRequestException("Adjustment exceeds the approved credit limit");
            type=KhathaLedgerEntryType.ADJUSTMENT_DEBIT;
            if(account.getDueDate()==null) account.setDueDate(LocalDate.now().plusDays(account.getRepaymentDays()));
        }
        account.setOutstandingBalance(next);
        if(next.signum()==0) account.setDueDate(null);
        accounts.save(account);
        KhathaLedgerEntry entry=newEntry(account,type,amount,next,"ADJUSTMENT",UUID.randomUUID().toString(),request.reason().trim(),actor);
        ledger.save(entry); writeAudit(account,actor,"BALANCE_ADJUSTMENT",request.reason().trim());
        return KhathaLedgerEntryResponse.from(entry);
    }

    @Transactional(readOnly=true)
    public List<KhathaAccountResponse> shopAccounts(Long shopId,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER); Shop shop=shops.findById(shopId).orElseThrow(()->new ResourceNotFoundException("Shop not found"));
        requireShop(shop,actor);
        return accounts.findShopAccounts(shopId).stream().map(KhathaAccountResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaCustomerResponse> shopCustomers(Long shopId,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER); Shop shop=shops.findById(shopId).orElseThrow(()->new ResourceNotFoundException("Shop not found"));
        requireShop(shop,actor);
        Map<Long,User> distinct=new LinkedHashMap<>();
        orders.findByShopId(shopId).forEach(order->{User customer=order.getCustomer(); if(customer!=null && customer.getRole()==Role.CUSTOMER && customer.isActive()) distinct.put(customer.getId(),customer);});
        return distinct.values().stream().sorted(Comparator.comparing(User::getName,String.CASE_INSENSITIVE_ORDER))
                .map(KhathaCustomerResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaLedgerEntryResponse> shopLedger(Long id,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER); KhathaAccount account=findAccount(id); requireShop(account.getShop(),actor);
        return ledger.findByAccountIdOrderByCreatedAtDescIdDesc(id).stream().map(KhathaLedgerEntryResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaRepaymentResponse> shopRepayments(Long id,String email) {
        User actor=requireUser(email,Role.SHOPKEEPER); KhathaAccount account=findAccount(id); requireShop(account.getShop(),actor);
        return repayments.findByAccountIdOrderByCreatedAtDesc(id).stream().map(KhathaRepaymentResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaAccountResponse> adminAccounts() {
        return accounts.findAllByOrderByUpdatedAtDesc().stream().map(KhathaAccountResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaLedgerEntryResponse> adminLedger(Long id) {
        if(!accounts.existsById(id)) throw new ResourceNotFoundException("Khatha account not found");
        return ledger.findByAccountIdOrderByCreatedAtDescIdDesc(id).stream().map(KhathaLedgerEntryResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<KhathaRepaymentResponse> adminRepayments() {
        return repayments.findAll().stream().map(KhathaRepaymentResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public KhathaAdminOverviewResponse adminOverview() {
        List<KhathaAccount> all=accounts.findAll();
        long active=all.stream().filter(a->a.getStatus()==KhathaAccountStatus.ACTIVE).count();
        long paused=all.stream().filter(a->a.getStatus()==KhathaAccountStatus.PAUSED).count();
        long overdue=all.stream().filter(this::isOverdue).count();
        BigDecimal total=all.stream().map(KhathaAccount::getOutstandingBalance).reduce(ZERO,BigDecimal::add);
        return new KhathaAdminOverviewResponse(all.size(),active,paused,
                repayments.countByStatus(KhathaRepaymentStatus.PENDING),overdue,total);
    }

    @Transactional
    public void chargeOrder(Order order,User customer) {
        KhathaAccount account=accounts.findByShopAndCustomerForUpdate(order.getShop().getId(),customer.getId())
                .orElseThrow(()->new BadRequestException("This shop has not offered you an Apniko Khatha account"));
        String orderId=String.valueOf(order.getId());
        if(ledger.existsByAccountIdAndSourceTypeAndSourceId(account.getId(),"ORDER",orderId)) return;
        if(!isUsable(account)) throw new BadRequestException("Your Khatha account is inactive, its terms need acceptance, or its balance is overdue");
        BigDecimal amount=money(BigDecimal.valueOf(order.getTotalAmount()));
        BigDecimal next=account.getOutstandingBalance().add(amount);
        if(next.compareTo(account.getCreditLimit())>0)
            throw new BadRequestException("This order exceeds your available Khatha credit");
        if(account.getOutstandingBalance().signum()==0) account.setDueDate(LocalDate.now().plusDays(account.getRepaymentDays()));
        account.setOutstandingBalance(next); accounts.save(account);
        ledger.save(newEntry(account,KhathaLedgerEntryType.PURCHASE_DEBIT,amount,next,"ORDER",orderId,
                "Khatha purchase for Apniko order #"+order.getId(),customer));
    }

    @Transactional
    public void reverseOrderDebit(Order order,User actor) {
        KhathaAccount account=accounts.findByShopAndCustomerForUpdate(order.getShop().getId(),order.getCustomer().getId())
                .orElseThrow(()->new BadRequestException("Khatha account for this order was not found"));
        String id=String.valueOf(order.getId());
        if(ledger.existsByAccountIdAndSourceTypeAndSourceId(account.getId(),"ORDER_REVERSAL",id)) return;
        KhathaLedgerEntry original=ledger.findByAccountIdAndSourceTypeAndSourceId(account.getId(),"ORDER",id)
                .orElseThrow(()->new BadRequestException("Original Khatha order debit was not found"));
        BigDecimal next=account.getOutstandingBalance().subtract(original.getAmount());
        if(next.signum()<0) throw new BadRequestException("Khatha reversal would create an invalid negative balance");
        account.setOutstandingBalance(next); if(next.signum()==0) account.setDueDate(null); accounts.save(account);
        ledger.save(newEntry(account,KhathaLedgerEntryType.ORDER_REVERSAL,original.getAmount(),next,
                "ORDER_REVERSAL",id,"Credit restored after cancellation of order #"+order.getId(),actor));
    }

    private KhathaRepayment findIdempotent(KhathaRepaymentRequest request,Long accountId) {
        String key=request.idempotencyKey();
        if(blank(key)) throw new BadRequestException("An idempotency key is required");
        return repayments.findByIdempotencyKey(key.trim()).map(previous->{
            if(!previous.getAccount().getId().equals(accountId))
                throw new BadRequestException("This idempotency key belongs to another account");
            if(previous.getAmount().compareTo(money(request.amount()))!=0
                    || previous.getChannel()!=request.channel()
                    || !Objects.equals(previous.getReference(),trim(request.reference())))
                throw new BadRequestException("This idempotency key was reused with different repayment details");
            return previous;
        }).orElse(null);
    }
    private KhathaRepayment newRepayment(KhathaAccount account,KhathaRepaymentRequest request,KhathaRepaymentStatus status) {
        KhathaRepayment r=new KhathaRepayment(); r.setAccount(account); r.setAmount(money(request.amount()));
        r.setChannel(request.channel()); r.setStatus(status); r.setIdempotencyKey(request.idempotencyKey().trim());
        r.setReference(trim(request.reference())); r.setNote(trim(request.note())); return r;
    }
    private void validateRepayment(KhathaAccount account,KhathaRepaymentRequest request) {
        BigDecimal amount=money(request.amount());
        if(amount.signum()<=0 || account.getOutstandingBalance().signum()<=0)
            throw new BadRequestException("There is no outstanding balance to repay");
        if(amount.compareTo(account.getOutstandingBalance())>0)
            throw new BadRequestException("Repayment cannot exceed the outstanding balance");
    }
    private void applyRepayment(KhathaAccount account,KhathaRepayment repayment,User actor,String source) {
        BigDecimal amount=money(repayment.getAmount());
        if(amount.compareTo(account.getOutstandingBalance())>0) throw new BadRequestException("Repayment exceeds the current outstanding balance");
        String sourceId=String.valueOf(repayment.getId());
        if(ledger.existsByAccountIdAndSourceTypeAndSourceId(account.getId(),source,sourceId)) return;
        BigDecimal next=account.getOutstandingBalance().subtract(amount);
        account.setOutstandingBalance(next); if(next.signum()==0) account.setDueDate(null);
        accounts.save(account);
        ledger.save(newEntry(account,KhathaLedgerEntryType.REPAYMENT_CREDIT,amount,next,source,sourceId,
                "Verified repayment #"+repayment.getId(),actor));
    }
    private KhathaLedgerEntry newEntry(KhathaAccount account,KhathaLedgerEntryType type,BigDecimal amount,
            BigDecimal balance,String sourceType,String sourceId,String description,User actor) {
        KhathaLedgerEntry e=new KhathaLedgerEntry(); e.setAccount(account); e.setEntryType(type);
        e.setAmount(money(amount)); e.setOutstandingAfter(money(balance)); e.setSourceType(sourceType);
        e.setSourceId(sourceId); e.setDescription(description); e.setDueDate(account.getDueDate());
        if(actor!=null){e.setActorName(actor.getName());e.setActorEmail(actor.getEmail());e.setActorRole(actor.getRole().name());}
        return e;
    }
    private void writeAudit(KhathaAccount account,User actor,String action,String reason) {
        KhathaAuditLog a=new KhathaAuditLog(); a.setAccountId(account.getId()); a.setAction(action);
        a.setReason(reason);a.setActorUserId(actor.getId());a.setActorName(actor.getName());
        a.setActorEmail(actor.getEmail());a.setActorRole(actor.getRole().name());audit.save(a);
    }
    private boolean isUsable(KhathaAccount a) {
        return a.getShop()!=null && a.getShop().isActive() && a.getStatus()==KhathaAccountStatus.ACTIVE && a.getAcceptedAt()!=null
                && a.getAcceptedTermsVersion()==a.getTermsVersion()
                && (a.getDueDate()==null || !a.getDueDate().isBefore(LocalDate.now()))
                && a.getOutstandingBalance().compareTo(a.getCreditLimit())<0;
    }
    private boolean isOverdue(KhathaAccount a) {
        return a.getOutstandingBalance().signum()>0 && a.getDueDate()!=null && a.getDueDate().isBefore(LocalDate.now());
    }
    private User requireUser(String email,Role role) {
        if(blank(email)) throw new BadRequestException("User is not authenticated");
        User u=users.findByEmail(email).orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(!u.isActive() || u.getRole()!=role) throw new AccessDeniedException("This account is not allowed to perform this Khatha action");
        return u;
    }
    private KhathaAccount findAccount(Long id) {return accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Khatha account not found"));}
    private KhathaAccount lockAccount(Long id) {return accounts.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Khatha account not found"));}
    private void requireCustomer(KhathaAccount a,User u) {
        if(a.getCustomer()==null || !a.getCustomer().getId().equals(u.getId())) throw new AccessDeniedException("You cannot access another customer's Khatha account");
    }
    private void requireShop(Shop shop,User actor) {
        if(shop==null || shop.getOwner()==null || !shop.getOwner().getId().equals(actor.getId())) throw new AccessDeniedException("You can manage Khatha only for your own shop");
    }
    private void verifyRepaymentAccount(KhathaRepayment r,Long id) {
        if(r.getAccount()==null || !r.getAccount().getId().equals(id)) throw new ResourceNotFoundException("Repayment request not found");
    }
    private BigDecimal money(BigDecimal value) {return value.setScale(2,RoundingMode.HALF_UP);}
    private boolean blank(String value) {return value==null || value.isBlank();}
    private String trim(String value) {return blank(value)?null:value.trim();}
}
