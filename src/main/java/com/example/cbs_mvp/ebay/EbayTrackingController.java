package com.example.cbs_mvp.ebay;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.entity.Order;
import com.example.cbs_mvp.service.TrackingService;

@RestController
@RequestMapping("/ebay/tracking")
public class EbayTrackingController {

    private final TrackingService service;

    public EbayTrackingController(TrackingService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/upload")
    public ResponseEntity<?> upload(@PathVariable Long orderId) {
        try {
            Order o = service.uploadTracking(orderId);
            return ResponseEntity.ok(Map.of("orderId", o.getOrderId(), "state", o.getState()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }
}
