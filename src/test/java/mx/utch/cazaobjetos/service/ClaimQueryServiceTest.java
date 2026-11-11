package mx.utch.cazaobjetos.service;
import java.util.*;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class ClaimQueryServiceTest {
    @Autowired DirectoryService directory;
    @Autowired ClaimQueryService queries;
    @Autowired ItemService items;
    @Autowired JdbcTemplate db;
    UUID finder,claimant,stranger,teacher,secretary,found,claim;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user CASCADE");
        finder=user("finder",UserRole.STUDENT,true);claimant=user("claimant",UserRole.STUDENT,true);stranger=user("stranger",UserRole.STUDENT,true);
        teacher=user("Docente",UserRole.TEACHER,true);secretary=user("Secretaria",UserRole.SECRETARY,true);user("Disabled",UserRole.TEACHER,false);
        found=items.createFound(finder,report());claim=items.claim(claimant,found,new ClaimRequestDto("Secreto del reclamante",null));
    }
    UUID user(String name,UserRole role,boolean enabled) {
        UUID id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,?)",id,name.toLowerCase()+"@utch.edu.mx","secret-hash",name,role.name(),enabled);return id;
    }
    ReportRequestDto report(){return new ReportRequestDto("Llaves","Llavero azul",Category.KEYS,"I",LocalDate.now());}
    ClaimQueryDto query(ClaimView view,ClaimStatus status,UUID id,int page,int size){return new ClaimQueryDto(status,view,id,page,size);}
    void missing(UUID actor,UUID id){assertEquals(404,assertThrows(BusinessException.class,()->queries.detail(actor,id)).getStatusCode().value());}
    @Test void directoryOnlyReturnsEnabledDelegatesWithLiteralSearchAndPaging() {
        var all=directory.list(new DirectoryQueryDto(null,null,0,1));assertEquals(2,all.total());assertEquals(teacher,all.items().getFirst().id());assertTrue(all.hasNext());
        assertEquals(secretary,directory.list(new DirectoryQueryDto(null,null,1,1)).items().getFirst().id());
        assertEquals(1,directory.list(new DirectoryQueryDto(" DOCENTE ",UserRole.TEACHER,0,20)).total());
        assertEquals(0,directory.list(new DirectoryQueryDto("%",null,0,20)).total());
        assertEquals(0,directory.list(new DirectoryQueryDto("disabled",null,0,20)).total());
        assertEquals(400,assertThrows(BusinessException.class,()->directory.list(new DirectoryQueryDto(null,UserRole.STUDENT,0,20))).getStatusCode().value());
    }
    @Test void claimFiltersAlwaysApplyParticipantScopeBeforePagination() {
        UUID another=items.claim(stranger,found,new ClaimRequestDto("Otro secreto",null));
        items.decide(finder,another,"reject");
        assertEquals(2,queries.search(finder,query(ClaimView.RECEIVED,null,found,0,1)).total());
        assertTrue(queries.search(finder,query(ClaimView.RECEIVED,null,found,0,1)).hasNext());
        assertEquals(1,queries.search(finder,query(ClaimView.RECEIVED,ClaimStatus.PENDING,found,0,20)).total());
        assertEquals(1,queries.search(claimant,query(ClaimView.SENT,null,null,0,20)).total());
        assertEquals(0,queries.search(claimant,query(ClaimView.RECEIVED,null,null,0,20)).total());
        assertEquals(0,queries.search(teacher,query(ClaimView.ALL,null,found,0,20)).total());
        var beyond=queries.search(finder,query(ClaimView.ALL,null,null,3,1));assertEquals(2,beyond.total());assertTrue(beyond.items().isEmpty());
        assertEquals(0,queries.search(finder,query(ClaimView.ALL,null,UUID.randomUUID(),0,20)).total());
    }
    @Test void detailOnlyExposesClaimToCurrentParticipantsAndDelegationTransfersAccess() {
        assertEquals("Secreto del reclamante",queries.detail(finder,claim).claim().privateDescription());
        assertEquals(claimant,queries.detail(claimant,claim).claimant().id());
        assertEquals(found,queries.detail(finder,claim).foundItem().id());
        missing(stranger,claim);missing(teacher,claim);missing(finder,UUID.randomUUID());
        items.delegate(finder,found,teacher);
        missing(finder,claim);assertEquals(teacher,queries.detail(claimant,claim).responsible().id());
        assertEquals(1,queries.search(teacher,query(ClaimView.RECEIVED,null,null,0,20)).total());
        assertEquals(0,queries.search(finder,query(ClaimView.ALL,null,null,0,20)).total());
    }
    @Test void closedClaimRemainsVisibleOnlyToParticipants() {
        items.decide(finder,claim,"reject");items.withdrawFound(finder,found);
        assertEquals(FoundStatus.WITHDRAWN,queries.detail(claimant,claim).foundItem().status());
        assertEquals(ClaimStatus.REJECTED,queries.detail(finder,claim).claim().status());missing(stranger,claim);
    }
}
