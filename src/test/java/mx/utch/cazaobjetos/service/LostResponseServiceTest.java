package mx.utch.cazaobjetos.service;

import java.time.LocalDate;
import java.util.UUID;
import mx.utch.cazaobjetos.dto.LostResponseRequestDto;
import mx.utch.cazaobjetos.dto.LostResponseCustodyRequestDto;
import mx.utch.cazaobjetos.enums.CustodyStatus;
import mx.utch.cazaobjetos.dto.ReportRequestDto;
import mx.utch.cazaobjetos.enums.Category;
import mx.utch.cazaobjetos.enums.LostStatus;
import mx.utch.cazaobjetos.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LostResponseServiceTest {
    @Autowired LostResponseService responses;
    @Autowired ItemService items;
    @Autowired NotificationService notifications;
    @Autowired JdbcTemplate db;
    @Autowired ProfilePhotoService profilePhotos;
    UUID owner, finder, other, loss;

    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user CASCADE");
        owner=user("Dueño"); finder=user("Paula"); other=user("Luis");
        loss=items.createLost(owner,new ReportRequestDto("Llaves","Llaves con funda",Category.KEYS,"I",LocalDate.now()));
    }

    private UUID user(String name) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",
            id,id+"@utch.edu.mx","unused",name,"STUDENT");
        return id;
    }

    @Test void replyReusesOriginalReportAndScopesConversationAndAvisos() {
        var request=new LostResponseRequestDto("I-3","Las vi junto a la puerta.",null);
        var response=responses.create(loss,finder,"reply-0001",request,null);
        assertEquals(response.id(),responses.create(loss,finder,"reply-0001",request,null).id());
        assertTrue(items.lost(loss).hasResponse());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM found_item",Integer.class));
        assertTrue(responses.list(loss,other).isEmpty());
        assertThrows(BusinessException.class,()->responses.comments(loss,response.id(),other));
        assertThrows(BusinessException.class,()->responses.photo(loss,response.id(),other));
        assertThrows(BusinessException.class,()->responses.decide(loss,response.id(),finder,true));
        var comment=responses.comment(loss,response.id(),owner,"message-0001","¿A qué hora puedo pasar?");
        assertEquals(comment.id(),responses.comment(loss,response.id(),owner,"message-0001","¿A qué hora puedo pasar?").id());
        assertEquals("Dueño",responses.comments(loss,response.id(),finder).getFirst().authorName());
        var alert=notifications.list(owner,mx.utch.cazaobjetos.enums.NotificationFilter.ALL,0,10).items().stream()
            .filter(n->response.id().equals(n.responseId())).findFirst().orElseThrow();
        assertEquals("Paula",alert.actorName());
        assertEquals("Llaves",alert.objectTitle());
        assertFalse(alert.body().contains("puerta"));
        assertEquals("COMPLETED",responses.decide(loss,response.id(),owner,true).status());
        assertEquals(LostStatus.RECOVERED,items.lost(loss).status());
        assertFalse(items.lost(loss).hasResponse());
        assertThrows(BusinessException.class,()->responses.comment(loss,response.id(),finder,null,"Otro comentario"));
        assertEquals(1,responses.comments(loss,response.id(),finder).size());
    }

    @Test void finderUpdatesCustodyWithPrivateHistoryAndCannotTakeItBackFromOffice() {
        var response=responses.create(loss,finder,null,new LostResponseRequestDto("I-3","Lo tengo conmigo.",null),null);
        assertEquals("WITH_FINDER",response.custody());
        var office=new LostResponseCustodyRequestDto(CustodyStatus.OFFICE,"Secretaría · edificio I");
        assertEquals(403,assertThrows(BusinessException.class,()->responses.custody(loss,response.id(),owner,office)).getStatusCode().value());
        assertEquals(404,assertThrows(BusinessException.class,()->responses.custody(loss,response.id(),other,office)).getStatusCode().value());
        assertEquals(400,assertThrows(BusinessException.class,()->responses.custody(loss,response.id(),finder,
            new LostResponseCustodyRequestDto(CustodyStatus.WITH_TEACHER,null))).getStatusCode().value());
        var updated=responses.custody(loss,response.id(),finder,office);
        assertEquals("OFFICE",updated.custody());
        assertEquals("Secretaría · edificio I",responses.list(loss,owner).getFirst().custodyLocation());
        assertEquals(updated,responses.custody(loss,response.id(),finder,office));
        assertEquals(1,responses.comments(loss,response.id(),owner).size());
        assertTrue(responses.comments(loss,response.id(),owner).getFirst().body().contains("Secretaría"));
        assertEquals(1,notifications.list(owner,mx.utch.cazaobjetos.enums.NotificationFilter.ALL,0,20).items().stream()
            .filter(n->n.kind()==mx.utch.cazaobjetos.enums.NotificationKind.CUSTODY_CHANGED && response.id().equals(n.responseId())).count());
        assertEquals(409,assertThrows(BusinessException.class,()->responses.custody(loss,response.id(),finder,
            new LostResponseCustodyRequestDto(CustodyStatus.WITH_FINDER,null))).getStatusCode().value());
        responses.custody(loss,response.id(),finder,new LostResponseCustodyRequestDto(CustodyStatus.OFFICE,"Oficina, mesa de la entrada"));
        assertEquals(2,responses.comments(loss,response.id(),owner).size());
        responses.decide(loss,response.id(),owner,true);
        assertEquals(409,assertThrows(BusinessException.class,()->responses.custody(loss,response.id(),finder,office)).getStatusCode().value());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM found_item",Integer.class));
    }

    @Test void newReplyCanStartInOfficeAndItsIdempotencyIncludesDelivery() {
        var request=new LostResponseRequestDto("I-3","Está en oficina.",null,CustodyStatus.OFFICE,"  ");
        var response=responses.create(loss,finder,"office-reply-1",request,null);
        assertEquals("OFFICE",response.custody());
        assertEquals("Oficina de profesores · edificio I",response.custodyLocation());
        assertEquals(response.id(),responses.create(loss,finder,"office-reply-1",request,null).id());
        assertThrows(BusinessException.class,()->responses.create(loss,finder,"office-reply-1",
            new LostResponseRequestDto("I-3","Está en oficina.",null,CustodyStatus.WITH_FINDER,null),null));
    }

    @Test void avatarsFollowProfileChangesAndRemainPrivate() throws Exception {
        var response=responses.create(loss,finder,null,new LostResponseRequestDto("I-3","Lo encontré.",null),null);
        responses.comment(loss,response.id(),finder,null,"Paso mañana.");
        assertNull(responses.comments(loss,response.id(),owner).getFirst().authorAvatarVersion());
        var out=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        profilePhotos.replace(finder+"@utch.edu.mx",new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",out.toByteArray()));
        db.update("UPDATE app_user SET degree='Tecnologías de la Información' WHERE id=?",finder);
        assertEquals("Tecnologías de la Información",responses.list(loss,owner).getFirst().finderDegree());
        assertEquals("Tecnologías de la Información",responses.comments(loss,response.id(),owner).getFirst().authorDegree());
        var version=responses.comments(loss,response.id(),owner).getFirst().authorAvatarVersion();
        assertNotNull(version);
        assertEquals(version,responses.list(loss,owner).getFirst().finderAvatarVersion());
        assertTrue(responses.avatar(loss,response.id(),owner,finder).getContent().length>0);
        assertEquals(404,assertThrows(BusinessException.class,()->responses.avatar(loss,response.id(),other,finder)).getStatusCode().value());
        assertEquals(404,assertThrows(BusinessException.class,()->responses.avatar(loss,response.id(),owner,other)).getStatusCode().value());
        profilePhotos.delete(finder+"@utch.edu.mx");
        assertNull(responses.comments(loss,response.id(),owner).getFirst().authorAvatarVersion());
    }

    @Test void withdrawnReportKeepsPrivateHistoryAndClosesOpenReplies() {
        var response=responses.create(loss,finder,null,new LostResponseRequestDto("I-3","Se quedó en la mesa.",null),null);
        items.withdrawLost(owner,loss);
        assertEquals("CANCELLED",responses.list(loss,finder).getFirst().status());
        assertEquals(LostStatus.WITHDRAWN,items.lost(loss,finder).status());
        assertThrows(BusinessException.class,()->items.lost(loss,other));
        assertThrows(BusinessException.class,()->responses.decide(loss,response.id(),owner,true));
    }
}
