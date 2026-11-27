package com.fixops.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

@Configuration
public class Routes {
    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                               @Value("${fixops.upstream.member}") String member,
                               @Value("${fixops.upstream.asset}") String asset,
                               @Value("${fixops.upstream.workorder}") String workorder,
                               @Value("${fixops.upstream.inventory}") String inventory,
                               @Value("${fixops.upstream.ai}") String ai) {
        return builder.routes()
                .route("member", r -> r.path("/api/members/**", "/api/organizations", "/api/organizations/**", "/api/invitations/**", "/api/platform/**", "/api/complexes/**", "/api/resident-enrollments", "/api/resident-enrollments/**").filters(f -> f.stripPrefix(1)).uri(member))
                .route("asset", r -> r.path("/api/assets", "/api/assets/**", "/api/locations", "/api/locations/**").uri(asset))
                .route("workorder", r -> r.path("/api/work-orders/**", "/api/schedules/**").filters(f -> f.stripPrefix(1)).uri(workorder))
                .route("inventory", r -> r.path("/api/inventory/**").filters(f -> f.stripPrefix(1)).uri(inventory))
                .route("ai", r -> r.path("/api/ai/**").filters(f -> f.stripPrefix(1)).uri(ai))
                .build();
    }
}
