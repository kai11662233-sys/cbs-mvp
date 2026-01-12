package com.example.cbs_mvp.ebay;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cbs_mvp.entity.EbayDraft;
import com.example.cbs_mvp.service.DraftService;

@RestController
@RequestMapping("/ebay/drafts")
public class EbayDraftController {

    private final DraftService draftService;

    public EbayDraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping("/{candidateId}/create")
    public ResponseEntity<?> create(@PathVariable Long candidateId) {
        try {
            EbayDraft draft = draftService.createDraft(candidateId);
            return ResponseEntity.ok(toResponse(draft));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<?> get(@PathVariable Long candidateId) {
        EbayDraft draft = draftService.getDraftByCandidateId(candidateId);
        if (draft == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "draft not found"));
        }
        return ResponseEntity.ok(toResponse(draft));
    }

    private static Map<String, Object> toResponse(EbayDraft draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("draftId", draft.getDraftId());
        body.put("candidateId", draft.getCandidateId());
        body.put("sku", draft.getSku());
        body.put("offerId", draft.getOfferId());
        body.put("state", draft.getState());
        body.put("lastError", draft.getLastError());
        return body;
    }
}
