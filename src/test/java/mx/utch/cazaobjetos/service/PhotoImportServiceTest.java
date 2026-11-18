package mx.utch.cazaobjetos.service;
import java.util.*;
import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.Category;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
@ActiveProfiles("test")
class PhotoImportServiceTest {
    @Autowired PhotoImportService imports;
    @Autowired PhotoService photos;
    @Autowired ItemService items;
    @Autowired JdbcTemplate db;
    @MockitoBean RemoteImageService remote;
    UUID owner,other,loss;
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE app_user CASCADE");owner=user("photo-owner");other=user("photo-other");
        loss=items.createLost(owner,new ReportRequestDto("Mouse","Mouse negro",Category.ELECTRONICS,"I",LocalDate.now()));
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        when(remote.download(anyString())).thenReturn(bytes.toByteArray());
    }
    UUID user(String name) {UUID id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,"STUDENT");return id;}
    @Test void importedReferenceIsValidatedStoredAndListed() {
        var image=imports.importPhoto(loss,owner,new ImportPhotoRequestDto("https://example.org/photo.png",true));
        assertEquals("image/png",image.contentType());assertTrue(image.referenceImage());
        assertTrue(photos.list(false,loss,owner).getFirst().referenceImage());
        assertNotNull(photos.content(false,loss,image.id(),owner).getContent());
    }
    @Test void checksPermissionsAndQuotaBeforeRequestingRemoteData() {
        var request=new ImportPhotoRequestDto("https://example.org/photo.png",true);
        assertEquals(403,assertThrows(BusinessException.class,()->imports.importPhoto(loss,other,request)).getStatusCode().value());
        verifyNoInteractions(remote);
        for(int i=0;i<3;i++)imports.importPhoto(loss,owner,request);
        assertEquals(409,assertThrows(BusinessException.class,()->imports.importPhoto(loss,owner,request)).getStatusCode().value());
        verify(remote,times(3)).download(anyString());
    }
    @Test void invalidDownloadedContentDoesNotLeaveAPhotoAndReferenceIsOptional() {
        when(remote.download(anyString())).thenReturn("<html>Not a photo</html>".getBytes());
        assertEquals(415,assertThrows(BusinessException.class,()->imports.importPhoto(loss,owner,new ImportPhotoRequestDto("https://example.org/page",true))).getStatusCode().value());
        assertTrue(photos.list(false,loss,owner).isEmpty());
    }
}
