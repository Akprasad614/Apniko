package com.localcart.controller;

import com.localcart.dto.*;
import com.localcart.entity.KhathaAccountStatus;
import com.localcart.security.SecurityUtils;
import com.localcart.service.KhathaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class KhathaController {
    private final KhathaService service;
    public KhathaController(KhathaService service){this.service=service;}

    @GetMapping("/customer/khatha")
    public ResponseEntity<List<KhathaAccountResponse>> customerAccounts(){
        return ResponseEntity.ok(service.customerAccounts(email()));
    }
    @GetMapping("/customer/khatha/shops/{shopId}")
    public ResponseEntity<KhathaAccountResponse> customerAccountForShop(@PathVariable Long shopId){
        return service.customerAccountForShop(shopId,email()).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());
    }
    @GetMapping("/customer/khatha/{accountId}")
    public ResponseEntity<KhathaAccountResponse> customerAccount(@PathVariable Long accountId){
        return ResponseEntity.ok(service.customerAccount(accountId,email()));
    }
    @GetMapping("/customer/khatha/{accountId}/ledger")
    public ResponseEntity<List<KhathaLedgerEntryResponse>> customerLedger(@PathVariable Long accountId){
        return ResponseEntity.ok(service.customerLedger(accountId,email()));
    }
    @PostMapping("/customer/khatha/{accountId}/accept")
    public ResponseEntity<KhathaAccountResponse> acceptTerms(@PathVariable Long accountId){
        return ResponseEntity.ok(service.acceptTerms(accountId,email()));
    }
    @PostMapping("/customer/khatha/{accountId}/repayments")
    public ResponseEntity<KhathaRepaymentResponse> requestRepayment(@PathVariable Long accountId,@Valid @RequestBody KhathaRepaymentRequest request){
        return ResponseEntity.ok(service.requestRepayment(accountId,request,email()));
    }

    @GetMapping("/shopkeeper/khatha")
    public ResponseEntity<List<KhathaAccountResponse>> shopAccounts(@RequestParam Long shopId){
        return ResponseEntity.ok(service.shopAccounts(shopId,email()));
    }
    @GetMapping("/shopkeeper/khatha/shops/{shopId}/customers")
    public ResponseEntity<List<KhathaCustomerResponse>> shopCustomers(@PathVariable Long shopId){
        return ResponseEntity.ok(service.shopCustomers(shopId,email()));
    }
    @PostMapping("/shopkeeper/khatha/offers")
    public ResponseEntity<KhathaAccountResponse> offerCredit(@Valid @RequestBody KhathaOfferRequest request){
        return ResponseEntity.ok(service.offerCredit(request,email()));
    }
    @GetMapping("/shopkeeper/khatha/{accountId}/ledger")
    public ResponseEntity<List<KhathaLedgerEntryResponse>> shopLedger(@PathVariable Long accountId){
        return ResponseEntity.ok(service.shopLedger(accountId,email()));
    }
    @GetMapping("/shopkeeper/khatha/{accountId}/repayments")
    public ResponseEntity<List<KhathaRepaymentResponse>> shopRepayments(@PathVariable Long accountId){
        return ResponseEntity.ok(service.shopRepayments(accountId,email()));
    }
    @PatchMapping("/shopkeeper/khatha/{accountId}/terms")
    public ResponseEntity<KhathaAccountResponse> updateTerms(@PathVariable Long accountId,@Valid @RequestBody KhathaTermsUpdateRequest request){
        return ResponseEntity.ok(service.updateTerms(accountId,request,email()));
    }
    @PatchMapping("/shopkeeper/khatha/{accountId}/status")
    public ResponseEntity<KhathaAccountResponse> updateStatus(@PathVariable Long accountId,@Valid @RequestBody KhathaStatusUpdateRequest request){
        return ResponseEntity.ok(service.updateStatus(accountId,request.status(),email()));
    }
    @PostMapping("/shopkeeper/khatha/{accountId}/repayments")
    public ResponseEntity<KhathaRepaymentResponse> recordRepayment(@PathVariable Long accountId,@Valid @RequestBody KhathaRepaymentRequest request){
        return ResponseEntity.ok(service.recordRepayment(accountId,request,email()));
    }
    @PostMapping("/shopkeeper/khatha/{accountId}/repayments/{repaymentId}/confirm")
    public ResponseEntity<KhathaRepaymentResponse> confirmRepayment(@PathVariable Long accountId,@PathVariable Long repaymentId){
        return ResponseEntity.ok(service.confirmRepayment(accountId,repaymentId,email()));
    }
    @PostMapping("/shopkeeper/khatha/{accountId}/repayments/{repaymentId}/reject")
    public ResponseEntity<KhathaRepaymentResponse> rejectRepayment(@PathVariable Long accountId,@PathVariable Long repaymentId,@RequestBody(required=false) KhathaRepaymentDecisionRequest request){
        return ResponseEntity.ok(service.rejectRepayment(accountId,repaymentId,request==null?null:request.reason(),email()));
    }
    @PostMapping("/shopkeeper/khatha/{accountId}/adjustments")
    public ResponseEntity<KhathaLedgerEntryResponse> adjustBalance(@PathVariable Long accountId,@Valid @RequestBody KhathaAdjustmentRequest request){
        return ResponseEntity.ok(service.adjustBalance(accountId,request,email()));
    }

    @GetMapping("/admin/khatha/overview")
    public ResponseEntity<KhathaAdminOverviewResponse> adminOverview(){return ResponseEntity.ok(service.adminOverview());}
    @GetMapping("/admin/khatha/accounts")
    public ResponseEntity<List<KhathaAccountResponse>> adminAccounts(){return ResponseEntity.ok(service.adminAccounts());}
    @GetMapping("/admin/khatha/{accountId}/ledger")
    public ResponseEntity<List<KhathaLedgerEntryResponse>> adminLedger(@PathVariable Long accountId){return ResponseEntity.ok(service.adminLedger(accountId));}
    @GetMapping("/admin/khatha/repayments")
    public ResponseEntity<List<KhathaRepaymentResponse>> adminRepayments(){return ResponseEntity.ok(service.adminRepayments());}

    private String email(){return SecurityUtils.getCurrentUserEmail();}
}
