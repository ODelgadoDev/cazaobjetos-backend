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

    public ItemService(ItemDataService data, UserDataService users, ItemEventService events) {
        this.data = data;
        this.users = users;
        this.events = events;
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
        require(!data.hasActiveClaims(id), HttpStatus.CONFLICT, "Resuelve o cancela los reclamos activos antes de editar");
        item.setTitle(request.title().strip());
        item.setDescription(request.description().strip());
        item.setCategory(request.category());
        item.setFoundLocation(request.location().strip());
        item.setFoundOn(request.occurredOn());
        events.audit(actor, id, "FOUND_EDITED");
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
        require(!data.hasActiveLinkedClaim(loss.getId()), HttpStatus.CONFLICT, "Resuelve o cancela el reclamo vinculado primero");
    }
    public void editLost(UUID actor, UUID id, ReportRequestDto request) {
        var loss = data.lockLoss(id);
        editableLoss(actor, loss);
        loss.setTitle(request.title().strip());
        loss.setDescription(request.description().strip());
        loss.setCategory(request.category());
        loss.setLostLocation(request.location().strip());
        loss.setLostOn(request.occurredOn());
        events.audit(actor, id, "LOSS_EDITED");
    }
    public void withdrawLost(UUID actor, UUID id) {
        var loss = data.lockLoss(id);
        require(actor.equals(loss.getOwnerId()), HttpStatus.FORBIDDEN, "Solo el dueño puede retirar el extravío");
        if (loss.getStatus() == LostStatus.WITHDRAWN) return;
        editableLoss(actor, loss);
        loss.setStatus(LostStatus.WITHDRAWN);
        events.audit(actor, id, "LOSS_WITHDRAWN");
    }
    @Transactional(readOnly = true)
    public List<ClaimResponseDto> claims(UUID actor, int page) { return data.claims(actor, page); }

    public UUID createFound(UUID actor, ReportRequestDto request) {
        UUID id = data.createFound(actor, request).getId();
        events.audit(actor, id, "FOUND_PUBLISHED");
        return id;
    }
    public UUID createLost(UUID actor, ReportRequestDto request) {
        UUID id = data.createLost(actor, request).getId();
        events.audit(actor, id, "LOSS_PUBLISHED");
        return id;
    }
    public void office(UUID actor, UUID id) {
        FoundItemModel item = data.lockItem(id);
        available(item);
        require(actor.equals(item.getReporterId()) || actor.equals(item.getResponsibleId()),
            HttpStatus.FORBIDDEN, "Solo el reportante o responsable puede registrar el resguardo");
        if (item.getCustody() == CustodyStatus.OFFICE) return;
        item.setCustody(CustodyStatus.OFFICE);
        item.setOfficeMarkedBy(actor);
        item.setOfficeMarkedAt(Instant.now());
        events.audit(actor, id, "LEFT_AT_OFFICE");
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
        return claimId;
    }
    public void decide(UUID actor, UUID claimId, String action) {
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
                data.closeOtherClaims(item.getId());
                if (claim.getLostReportId() != null) recover(data.lockLoss(claim.getLostReportId()));
                events.recovered(item, users.user(item.getReporterId()).getEmail());
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
