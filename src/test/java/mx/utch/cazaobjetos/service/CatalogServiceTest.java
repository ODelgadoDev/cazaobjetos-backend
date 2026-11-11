package mx.utch.cazaobjetos.service;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CatalogServiceTest {
    @Autowired CatalogService catalog;
    @Autowired JdbcTemplate db;
    private final UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID id(int n) { return UUID.fromString("10000000-0000-0000-0000-" + String.format("%012d", n)); }
    @BeforeEach void prepare() {
        db.execute("TRUNCATE email_verification,email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",user,"catalog@utch.edu.mx","unused","Catálogo","STUDENT");
        found(1,"Mochila negra","Con cierre rojo","CLOTHING","Edificio I, salón 3","2026-10-01","AVAILABLE");
        found(2,"Calculadora CASIO","Pantalla gris","ELECTRONICS","Laboratorio I","2026-10-02","AVAILABLE");
        found(3,"Llaves","Llavero azul","KEYS","Edificio I, salón 3","2026-10-03","RETURNED");
        found(4,"Etiqueta 100%_!","Texto literal","OTHER","Oficina","2026-10-04","AVAILABLE");
        found(5,"mochila AZUL","Exterior liso","CLOTHING","Biblioteca","2026-10-01","AVAILABLE");
        db.update("UPDATE found_item SET custody='OFFICE',office_marked_by=?,office_marked_at=now() WHERE id=?",user,id(2));
        db.update("INSERT INTO lost_report(id,owner_id,title,description,category,lost_location,lost_on,status) VALUES (?,?,?,?,?,?,?,?)",id(6),user,"Calculadora","Pantalla gris","ELECTRONICS","Laboratorio I",LocalDate.of(2026,10,2),"ACTIVE");
        db.update("INSERT INTO lost_report(id,owner_id,title,description,category,lost_location,lost_on,status) VALUES (?,?,?,?,?,?,?,?)",id(7),user,"Llaves","Llavero rojo","KEYS","Salón 3",LocalDate.of(2026,10,3),"RECOVERED");
        db.update("INSERT INTO claim(id,found_item_id,claimant_id,private_description) VALUES (?,?,?,?)",id(8),id(1),user,"secreto-invisible-en-catalogo");
    }
    void found(int n,String title,String description,String category,String location,String date,String status) {
        db.update("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on,status) VALUES (?,?,?,?,?,?,?,?,?)",
                id(n),user,user,title,description,category,location,LocalDate.parse(date),status);
    }
    CatalogQueryDto query(String q, Category category, String location, String status, CustodyStatus custody,
                          String from,String to,CatalogSort sort,int page,int size) {
        return new CatalogQueryDto(q,category,location,status,custody,from==null?null:LocalDate.parse(from),to==null?null:LocalDate.parse(to),sort,page,size);
    }
    CatalogQueryDto text(String q) { return query(q,null,null,null,null,null,null,null,0,20); }
    @Test void searchesTitleDescriptionAndLocationIgnoringCaseAndWhitespace() {
        assertEquals(2,catalog.found(text("  MOCHILA  ")).total());
        assertEquals(id(1),catalog.found(text("CIERRE ROJO")).items().getFirst().id());
        assertEquals(2,catalog.found(text("edificio i")).total());
        assertEquals(0,catalog.found(text("secreto-invisible-en-catalogo")).total());
    }
    @Test void combinesAllFiltersUsingAndWithInclusiveDates() {
        var query=query("casio",Category.ELECTRONICS,"laboratorio","AVAILABLE",CustodyStatus.OFFICE,"2026-10-02","2026-10-02",null,0,20);
        var result=catalog.found(query);
        assertEquals(1,result.total()); assertEquals(id(2),result.items().getFirst().id());
        assertEquals(0,catalog.found(query("casio",Category.KEYS,null,null,null,null,null,null,0,20)).total());
        assertEquals(2,catalog.found(query(null,null,null,null,null,"2026-10-02","2026-10-03",null,0,20)).total());
    }
    @Test void treatsWildcardsAndSqlFragmentsAsLiteralInput() {
        for(String text:List.of("%","_","!","100%_!")) {
            assertEquals(1,catalog.found(text(text)).total());
            assertEquals(id(4),catalog.found(text(text)).items().getFirst().id());
        }
        assertEquals(0,catalog.found(text("' OR 1=1 --")).total());
    }
    @Test void paginatesAndCountsAfterFilteringWithStableTies() {
        var first=catalog.found(query("mochila",null,null,null,null,null,null,CatalogSort.RECENT,0,1));
        var second=catalog.found(query("mochila",null,null,null,null,null,null,CatalogSort.RECENT,1,1));
        assertEquals(2,first.total()); assertEquals(2,first.totalPages()); assertEquals(1,first.pageSize());
        assertTrue(first.hasNext()); assertFalse(first.hasPrevious());
        assertFalse(second.hasNext()); assertTrue(second.hasPrevious());
        assertEquals(id(1),first.items().getFirst().id()); assertEquals(id(5),second.items().getFirst().id());
        var empty=catalog.found(query("mochila",null,null,null,null,null,null,null,9,1));
        assertTrue(empty.items().isEmpty()); assertEquals(2,empty.total());
        var none=catalog.found(text("no-existe"));assertEquals(0,none.totalPages());assertFalse(none.hasNext());
    }
    @Test void supportsDateAndTitleOrdersAndStatusSelection() {
        assertEquals(id(4),catalog.found(text("")).items().getFirst().id());
        assertEquals(id(1),catalog.found(query(null,null,null,null,null,null,null,CatalogSort.OLDEST,0,20)).items().getFirst().id());
        assertEquals(id(2),catalog.found(query(null,null,null,null,null,null,null,CatalogSort.TITLE_ASC,0,20)).items().getFirst().id());
        assertEquals(id(1),catalog.found(query(null,null,null,null,null,null,null,CatalogSort.TITLE_DESC,0,20)).items().getFirst().id());
        assertEquals(4,catalog.found(query(null,null,null,"AVAILABLE",null,null,null,null,0,20)).total());
        assertEquals(id(3),catalog.found(query(null,null,null,"RETURNED",null,null,null,null,0,20)).items().getFirst().id());
    }
    @Test void lostReportsShareSearchAndPagingButUseTheirOwnStates() {
        var result=catalog.lost(query("pantalla",Category.ELECTRONICS,"Laboratorio","ACTIVE",null,"2026-10-02","2026-10-02",null,0,1));
        assertEquals(1,result.total());assertEquals(id(6),result.items().getFirst().id());
        assertEquals(1,catalog.lost(query(null,null,null,"RECOVERED",null,null,null,null,0,20)).total());
        assertThrows(BusinessException.class,()->catalog.lost(query(null,null,null,null,CustodyStatus.OFFICE,null,null,null,0,20)));
        assertThrows(BusinessException.class,()->catalog.found(query(null,null,null,"ACTIVE",null,null,null,null,0,20)));
    }
}
