package com.example.cbs_mvp.threepl;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.service.ThreePlService;
import com.example.cbs_mvp.service.ThreePlService.ImportResult;

@RestController
@RequestMapping("/3pl")
public class ThreePlController {

    private final ThreePlService service;

    public ThreePlController(ThreePlService service) {
        this.service = service;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<?> exportCsv(@RequestParam(defaultValue = "10") int limit) {
        try {
            String csv = service.exportRequestedPos(limit);
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("text/csv"))
                    .body(csv);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }

    @PostMapping(value = "/import-tracking", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> importTracking(@RequestBody String body) {
        try {
            return ResponseEntity.ok(service.importTrackingCsv(body));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        }
    }
}
