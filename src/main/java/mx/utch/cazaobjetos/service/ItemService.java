package mx.utch.cazaobjetos.service;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.*;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class ItemService {
    private final ItemDataService data;
    private final UserDataService users;
    private final ItemEventService events;
    private final ClaimJournalService journal;
    private final NotificationEventService notifications;
    private final MilestoneEmailService milestoneMail;

    public ItemService(ItemDataService data, UserDataService users, ItemEventService events, ClaimJournalService journal, NotificationEventService notifications, MilestoneEmailService milestoneMail) {
        this.data = data;
        this.users = users;
        this.events = events;
        this.journal = journal;
        this.notifications = notifications;
        this.milestoneMail=milestoneMail;
    }

    private void available(FoundItemModel item) {
        require(item.getStatus() == FoundStatus.AVAILABLE, HttpStatus.CONFLICT, "El hallazgo no está disponible");
    }
    private void responsible(FoundItemModel item, UUID actor) {
        require(actor.equals(item.getResponsibleId()), HttpStatus.FORBIDDEN, "Solo el responsable puede realizar esta acción");
    }

    @Transactional(readOnly = true)
    public FoundItemResponseDto found(UUID id) {
        var item = data.found(id);
        require(item.getStatus() != FoundStatus.WITHDRAWN, HttpStatus.NOT_FOUND, "Publicación no disponible");
        return item.toResponseDto();
    }
    @Transactional(readOnly = true)
    public LostReportResponseDto lost(UUID id) {
        var loss = data.lost(id);
        require(loss.getStatus() != LostStatus.WITHDRAWN, HttpStatus.NOT_FOUND, "Publicación no disponible");
        return loss.toResponseDto();
    }
    public void editFound(UUID actor, UUID id, ReportRequestDto request) {
        var item = data.lockItem(id);
        responsible(item, actor);
        available(item);
        var before=item.toResponseDto();
        item.setTitle(request.title().strip());
        item.setDescription(request.description().strip());
        item.setCategory(request.category());
        item.setFoundLocation(request.location().strip());
        item.setFoundOn(request.occurredOn());
        events.audit(actor, id, "FOUND_EDITED");
        journal.foundEvent(item,actor,ClaimActivityKind.PUBLICATION_EDITED,"Se actualizó la ficha del hallazgo.",Map.of("before",before,"after",item.toResponseDto()));
    }
    public void withdrawFound(UUID actor, UUID id) {
        var item = data.lockItem(id);
        responsible(item, actor);
        if (item.getStatus() == FoundStatus.WITHDRAWN) return;
        available(item);
        require(!data.hasActiveClaims(id), HttpStatus.CONFLICT, "Resuelve o cancela los reclamos activos antes de retirar");
        item.setStatus(FoundStatus.WITHDRAWN);
        events.audit(actor, id, "FOUND_WITHDRAWN");
    }
    private void editableLoss(UUID actor, LostReportModel loss) {
        require(actor.equals(loss.getOwnerId()), HttpStatus.FORBIDDEN, "Solo el dueño puede modificar el extravío");
        require(loss.getStatus() == LostStatus.ACTIVE, HttpStatus.CONFLICT, "El extravío no está activo");

    }
    public void editLost(UUID actor, UUID id, ReportRequestDto request) {
        var loss = data.lockLoss(id);
        editableLoss(actor, loss);
        var before=loss.toResponseDto();
        loss.setTitle(request.title().strip());
        loss.setDescription(request.description().strip());
        loss.setCategory(request.category());
        loss.setLostLocation(request.location().strip());
        loss.setLostOn(request.occurredOn());
        events.audit(actor, id, "LOSS_EDITED");
        journal.lossEvent(id,actor,before,loss.toResponseDto());
    }
    public void withdrawLost(UUID actor, UUID id) {
        var loss = data.lockLoss(id);
        require(actor.equals(loss.getOwnerId()), HttpStatus.FORBIDDEN, "Solo el dueño puede retirar el extravío");
        if (loss.getStatus() == LostStatus.WITHDRAWN) return;
        editableLoss(actor, loss);
        require(!data.hasActiveLinkedClaim(id),HttpStatus.CONFLICT,"Resuelve o cancela el reclamo vinculado antes de retirar");
        loss.setStatus(LostStatus.WITHDRAWN);
        events.audit(actor, id, "LOSS_WITHDRAWN");
    }
    @Transactional(readOnly = true)
    public List<ClaimResponseDto> claims(UUID actor, int page) { return data.claims(actor, page); }

    public UUID createFound(UUID actor, ReportRequestDto request) {
        UUID id = data.createFound(actor, request).getId();
        events.audit(actor, id, "FOUND_PUBLISHED");
        notifications.published(actor,id,true);milestoneMail.published(actor,id,true);
        return id;
    }
    public UUID createLost(UUID actor, ReportRequestDto request) {
        UUID id = data.createLost(actor, request).getId();
        events.audit(actor, id, "LOSS_PUBLISHED");
        notifications.published(actor,id,false);milestoneMail.published(actor,id,false);
        return id;
    }
    public void office(UUID actor, UUID id) {
        var item=data.lockItem(id);
        available(item);custodyActor(item,actor);
        if(item.getCustody()==CustodyStatus.OFFICE)return;
        applyCustody(actor,item,new CustodyRequestDto(CustodyStatus.OFFICE,null,"Oficina de profesores · edificio I"));
    }
    private void custodyActor(FoundItemModel item,UUID actor) {
        require(actor.equals(item.getReporterId())||actor.equals(item.getResponsibleId()),HttpStatus.FORBIDDEN,"Solo quien encontró el objeto o su responsable puede cambiar el resguardo");
    }
    private String text(String raw) {
        if(raw==null||raw.isBlank())return null;
        String value=raw.strip();require(value.codePoints().noneMatch(Character::isISOControl),HttpStatus.BAD_REQUEST,"El resguardo debe escribirse en una sola línea");return value;
    }
    public void custody(UUID actor,UUID id,CustodyRequestDto request) {
        var item=data.lockItem(id);available(item);custodyActor(item,actor);applyCustody(actor,item,request);
    }
    private void applyCustody(UUID actor,FoundItemModel item,CustodyRequestDto request) {
        require(request.custody()!=null,HttpStatus.BAD_REQUEST,"Selecciona el resguardo");
        require(item.getCustody()!=CustodyStatus.OFFICE||request.custody()==CustodyStatus.OFFICE,HttpStatus.CONFLICT,"El objeto permanece en oficina hasta su entrega");
        String teacher=request.custody()==CustodyStatus.WITH_TEACHER?text(request.teacherName()):null;
        String location=request.custody()==CustodyStatus.WITH_FINDER?null:text(request.location());
        require(request.custody()!=CustodyStatus.WITH_TEACHER||teacher!=null,HttpStatus.BAD_REQUEST,"Escribe con qué docente quedó el objeto");
        require(teacher==null||teacher.length()<=120,HttpStatus.BAD_REQUEST,"El nombre del docente admite 120 caracteres");
        require(location==null||location.length()<=200,HttpStatus.BAD_REQUEST,"La ubicación admite 200 caracteres");
        if(item.getCustody()==request.custody()&&Objects.equals(item.getTeacherName(),teacher)&&Objects.equals(item.getCustodyLocation(),location))return;
        var before=item.toResponseDto();item.setCustody(request.custody());item.setTeacherName(teacher);item.setCustodyLocation(location);
        item.setCustodyMarkedBy(actor);item.setCustodyMarkedAt(Instant.now());
        if(request.custody()==CustodyStatus.OFFICE&&item.getOfficeMarkedAt()==null) {item.setOfficeMarkedBy(actor);item.setOfficeMarkedAt(Instant.now());}
        events.audit(actor,item.getId(),request.custody()==CustodyStatus.OFFICE?"LEFT_AT_OFFICE":"CUSTODY_CHANGED");
        journal.foundEvent(item,actor,ClaimActivityKind.CUSTODY_CHANGED,"Se actualizó el lugar de resguardo. La gestión del reclamo se conserva.",Map.of("before",before,"after",item.toResponseDto()));
    }
    public void delegate(UUID actor, UUID id, UUID target) {
        FoundItemModel item = data.lockItem(id);
        available(item);
        responsible(item, actor);
        UserModel targetUser = users.enabledUser(target);
        require(Set.of(UserRole.TEACHER, UserRole.SECRETARY).contains(targetUser.getRole()),
            HttpStatus.BAD_REQUEST, "Solo se delega a docentes o secretaria");
        require(!target.equals(actor), HttpStatus.CONFLICT, "Ya eres responsable");
        require(!data.hasActiveClaim(id, target), HttpStatus.CONFLICT, "No se puede delegar al reclamante");
        item.setResponsibleId(target);
        events.audit(actor, id, "DELEGATED_TO_" + target);
        journal.foundEvent(item,actor,ClaimActivityKind.DELEGATED,"Se cambió el responsable de un hallazgo del flujo anterior.",Map.of("responsibleId",target));
    }
    public UUID claim(UUID actor, UUID id, ClaimRequestDto request) {
        FoundItemModel item = data.lockItem(id);
        available(item);
        require(!actor.equals(item.getReporterId()) && !actor.equals(item.getResponsibleId()),
            HttpStatus.CONFLICT, "No puedes reclamar tu propio hallazgo o uno que gestionas");
        if (request.lostReportId() != null) {
            LostReportModel loss = data.lockLoss(request.lostReportId());
            require(actor.equals(loss.getOwnerId()), HttpStatus.FORBIDDEN, "El extravío debe pertenecerte");
            require(loss.getStatus() == LostStatus.ACTIVE, HttpStatus.CONFLICT, "El extravío ya está cerrado");
        }
        UUID claimId = data.createClaim(actor, id, request).getId();
        events.audit(actor, claimId, "CLAIM_SUBMITTED");
        journal.event(claimId,actor,ClaimActivityKind.SUBMITTED,"El solicitante abrió el reclamo.",Map.of("foundItem",item.toResponseDto()));
        return claimId;
    }
    public void decide(UUID actor, UUID claimId, String action) { decide(actor,claimId,action,null); }
    public void decide(UUID actor, UUID claimId, String action, String rawReason) {
        String reason=rawReason==null||rawReason.isBlank()?null:rawReason.strip();
        require(reason==null||reason.length()<=2000,HttpStatus.BAD_REQUEST,"El motivo admite 2000 caracteres");
        require(reason==null||reason.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),HttpStatus.BAD_REQUEST,"El motivo contiene caracteres inválidos");
        // Lock parent before loading claim state, including under concurrent completion.
        FoundItemModel item = data.lockItem(data.itemIdForClaim(claimId));
        ClaimModel claim = data.claim(claimId);
        switch (action) {
            case "complete" -> {
                require(actor.equals(claim.getClaimantId()), HttpStatus.FORBIDDEN,
                    "Solo el solicitante puede confirmar que recuperó el objeto");
                if (claim.getStatus() == ClaimStatus.COMPLETED) return;
                available(item);
                require(claim.getStatus() == ClaimStatus.ACCEPTED, HttpStatus.CONFLICT,
                    "El responsable debe aceptar primero el reclamo");
                claim.setStatus(ClaimStatus.COMPLETED);
                claim.setCompletedAt(Instant.now());
                item.setStatus(FoundStatus.RETURNED);
                data.closeOtherClaims(item.getId()).forEach(closed -> journal.event(closed.getId(),null,ClaimActivityKind.CLOSED,"El hallazgo se entregó y esta solicitud quedó cerrada.",null));
                if (claim.getLostReportId() != null) recover(data.lockLoss(claim.getLostReportId()));
            }
            case "accept" -> {
                responsible(item, actor);
                available(item);
                if (claim.getStatus() == ClaimStatus.ACCEPTED) return;
                require(claim.getStatus() == ClaimStatus.PENDING, HttpStatus.CONFLICT, "El reclamo no está pendiente");
                require(!data.hasAcceptedClaim(item.getId()), HttpStatus.CONFLICT, "Ya existe un reclamo aceptado");
                claim.setStatus(ClaimStatus.ACCEPTED);
                claim.setAcceptedBy(actor);
                claim.setAcceptedAt(Instant.now());
            }
            case "reject" -> {
                responsible(item, actor);
                available(item);
                require(claim.getStatus() == ClaimStatus.PENDING, HttpStatus.CONFLICT, "Solo se rechazan reclamos pendientes");
                claim.setStatus(ClaimStatus.REJECTED);
            }
            case "cancel" -> {
                require(actor.equals(claim.getClaimantId()), HttpStatus.FORBIDDEN, "Solo el solicitante puede cancelar");
                available(item);
                require(Set.of(ClaimStatus.PENDING, ClaimStatus.ACCEPTED).contains(claim.getStatus()),
                    HttpStatus.CONFLICT, "El reclamo ya está cerrado");
                claim.setStatus(ClaimStatus.CANCELLED);
            }
            default -> throw new BusinessException(HttpStatus.NOT_FOUND, "Acción no disponible");
        }
        events.audit(actor, claimId, "CLAIM_" + action.toUpperCase(Locale.ROOT));
        var kind=switch(action) {case "accept"->ClaimActivityKind.ACCEPTED;case "reject"->ClaimActivityKind.REJECTED;case "cancel"->ClaimActivityKind.CANCELLED;default->ClaimActivityKind.COMPLETED;};
        var message=switch(action) {case "accept"->"El responsable aceptó el reclamo. Pueden coordinar la entrega.";case "reject"->"El responsable rechazó el reclamo.";case "cancel"->"El solicitante canceló el reclamo.";default->"El solicitante confirmó que recibió el objeto. Expediente finalizado.";};
        journal.event(claimId,actor,kind,reason==null?message:message+"\nMotivo: "+reason,Map.of("foundItem",item.toResponseDto()));
    }
    public void recoverLost(UUID actor, UUID id) {
        LostReportModel loss = data.lockLoss(id);
        require(actor.equals(loss.getOwnerId()), HttpStatus.FORBIDDEN, "Solo puedes cerrar tu propio extravío");
        if (loss.getStatus() == LostStatus.RECOVERED) return;
        require(loss.getStatus() == LostStatus.ACTIVE, HttpStatus.CONFLICT, "El extravío fue retirado");
        require(!data.hasActiveLinkedClaim(id), HttpStatus.CONFLICT,
            "Completa o cancela el reclamo vinculado antes de cerrar este extravío");
        recover(loss);
        events.audit(actor, id, "LOSS_RECOVERED_EXTERNALLY");
    }
    private void recover(LostReportModel loss) {
        loss.setStatus(LostStatus.RECOVERED);
        loss.setRecoveredAt(Instant.now());
    }
}
