package com.localcart.dto;
import com.localcart.entity.User;
public record KhathaCustomerResponse(Long id,String name,String email) {
 public static KhathaCustomerResponse from(User u){return new KhathaCustomerResponse(u.getId(),u.getName(),u.getEmail());}
}
