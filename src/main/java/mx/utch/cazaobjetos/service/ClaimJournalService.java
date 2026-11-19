package mx.utch.cazaobjetos.service;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import mx.utch.cazaobjetos.dto.ClaimActivityDto;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.repository.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class ClaimJournalService {
    private final ClaimActivityRepository activities;
    private final ClaimRepository claims;
    private final UserDataService users;
    private final ObjectMapper json;
    private final NotificationEventService notifications;
    private final MilestoneEmailService milestoneMail;
    public ClaimJournalService(ClaimActivityRepository activities,ClaimRepository claims,UserDataService users,ObjectMapper json,NotificationEventService notifications,MilestoneEmailService milestoneMail) {
        this.activities=activities;this.claims=claims;this.users=users;this.json=json;this.notifications=notifications;this.milestoneMail=milestoneMail;
    }
    public ClaimActivityModel append(UUID claim,UUID actor,ClaimActivityKind kind,String body,Object details,UUID evidence,String key,String hash) {
        var entry=new ClaimActivityModel();entry.setId(UUID.randomUUID());entry.setClaimId(claim);entry.setActorId(actor);
        entry.setActorName(actor==null?"Sistema":users.user(actor).getDisplayName());entry.setKind(kind);entry.setBody(body);
        entry.setDetailsJson(details==null?null:json.writeValueAsString(details));entry.setEvidenceId(evidence);
        entry.setRequestKey(key);entry.setRequestHash(hash);entry.setCreatedAt(Instant.now());
        activities.saveAndFlush(entry);notifications.activity(entry);milestoneMail.activity(entry);return entry;
    }
    public void event(UUID claim,UUID actor,ClaimActivityKind kind,String body,Object details) { append(claim,actor,kind,body,details,null,null,null); }
    public void foundEvent(FoundItemModel item,UUID actor,ClaimActivityKind kind,String body,Object details) {
        claims.findByFoundItemIdAndStatusIn(item.getId(),List.of(ClaimStatus.PENDING,ClaimStatus.ACCEPTED))
            .forEach(claim->event(claim.getId(),actor,kind,body,details));
    }
    public void lossEvent(UUID loss,UUID actor,Object before,Object after) {
        claims.findByLostReportIdAndStatusIn(loss,List.of(ClaimStatus.PENDING,ClaimStatus.ACCEPTED))
            .forEach(claim->event(claim.getId(),actor,ClaimActivityKind.PUBLICATION_EDITED,"El solicitante actualizó su reporte de extravío.",Map.of("before",before,"after",after)));
    }
    public static ClaimActivityDto response(ClaimActivityModel e) {
        return new ClaimActivityDto(e.getId(),e.getKind(),e.getActorId(),e.getActorName(),e.getBody(),e.getDetailsJson(),e.getEvidenceId(),e.getCreatedAt(),e.getActorAvatarVersion(),e.getActorDegree());
    }
}
