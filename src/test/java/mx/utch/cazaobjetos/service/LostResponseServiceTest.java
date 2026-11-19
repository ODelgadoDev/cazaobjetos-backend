package mx.utch.cazaobjetos.service;

import java.time.LocalDate;
import java.util.UUID;
import mx.utch.cazaobjetos.dto.LostResponseRequestDto;
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

    @Test void withdrawnReportKeepsPrivateHistoryAndClosesOpenReplies() {
        var response=responses.create(loss,finder,null,new LostResponseRequestDto("I-3","Se quedó en la mesa.",null),null);
        items.withdrawLost(owner,loss);
        assertEquals("CANCELLED",responses.list(loss,finder).getFirst().status());
        assertEquals(LostStatus.WITHDRAWN,items.lost(loss,finder).status());
        assertThrows(BusinessException.class,()->items.lost(loss,other));
        assertThrows(BusinessException.class,()->responses.decide(loss,response.id(),owner,true));
    }
}
