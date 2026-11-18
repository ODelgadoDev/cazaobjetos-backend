package mx.utch.cazaobjetos.service;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.NotificationFilter;
import mx.utch.cazaobjetos.repository.NotificationRepository;
import mx.utch.cazaobjetos.exception.BusinessException;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional
public class NotificationService {
    private final NotificationRepository data;
    public NotificationService(NotificationRepository data) {this.data=data;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponseDto<NotificationResponseDto> list(UUID actor,NotificationFilter filter,int page,int size) {
        require(filter!=null&&page>=0&&page<=10000&&size>=1&&size<=100,HttpStatus.BAD_REQUEST,"Paginación o filtro inválido");
        return PageResponseDto.from(data.visible(actor,filter==NotificationFilter.UNREAD,PageRequest.of(page,size)).map(n->n.toResponseDto()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public NotificationSummaryDto summary(UUID actor) {return new NotificationSummaryDto(data.visibleCount(actor,true),data.visibleCount(actor,false));}
    public NotificationResponseDto read(UUID actor,UUID id) {
        require(data.markRead(actor,id,Instant.now())==1,HttpStatus.NOT_FOUND,"Notificación no disponible");
        return data.visibleById(actor,id).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Notificación no disponible")).toResponseDto();
    }
    public NotificationClearDto clearRead(UUID actor) {return new NotificationClearDto(data.clearRead(actor,Instant.now()));}
}
