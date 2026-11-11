package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.enums.*;
public record ClaimQueryDto(ClaimStatus status,ClaimView view,UUID foundItemId,
        @Min(0) @Max(10000) Integer page,@Min(1) @Max(100) Integer pageSize) {
    public ClaimQueryDto { view=view==null?ClaimView.ALL:view;page=page==null?0:page;pageSize=pageSize==null?20:pageSize; }
}
