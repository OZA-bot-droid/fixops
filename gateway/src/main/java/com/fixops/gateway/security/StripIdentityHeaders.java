package com.fixops.gateway.security;
import org.springframework.context.annotation.*;import org.springframework.web.server.WebFilter;
@Configuration
public class StripIdentityHeaders {
 @Bean WebFilter removeSpoofedIdentityHeaders(){
  return (exchange,chain)->chain.filter(exchange.mutate().request(exchange.getRequest().mutate().headers(h->{
   h.remove("X-Member-Id");h.remove("X-User-Role");h.remove("X-Org-Id");h.remove("X-User-Id");
  }).build()).build());
 }
}
