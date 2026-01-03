package com.example.cbs_mvp.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.cbs_mvp.entity.StateTransition;
import com.example.cbs_mvp.repo.StateTransitionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StateTransitionService {

    private final StateTransitionRepository repo;

    public void log(
            String entityType,
            Long entityId,
            String fromState,
            String toState,
            String reasonCode,
            String reasonDetail,
            String actor,
            String correlationId
    ) {
        StateTransition st = new StateTransition();
        st.setEntityType(entityType);
        st.setEntityId(entityId);
        st.setFromState(fromState);
        st.setToState(toState);
        st.setReasonCode(reasonCode);
        st.setReasonDetail(reasonDetail);
        st.setActor(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        st.setCorrelationId(correlationId);
        st.setCreatedAt(LocalDateTime.now());
        repo.save(st);
    }
}
