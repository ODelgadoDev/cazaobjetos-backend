package mx.utch.cazaobjetos.service;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import nl.martijndwars.webpush.Utils;
@Component
public class VapidKeys {
    private final boolean enabled;
    private KeyPair pair;
    private String publicKey;
    public VapidKeys(@Value("${app.push.enabled:true}") boolean enabled,@Value("${app.push.key-file:.local/vapid.properties}") String file) {
        this.enabled=enabled;if(!enabled)return;
        synchronized(VapidKeys.class) {
            if(Security.getProvider("BC")==null)Security.addProvider(new BouncyCastleProvider());
            Path path=Path.of(file).toAbsolutePath();
            try {
                boolean newDirectory=!Files.exists(path.getParent());Files.createDirectories(path.getParent());if(newDirectory)setPermissions(path.getParent(),"rwx------");
                Path lockFile=path.resolveSibling(path.getFileName()+".lock");
                try(var lockChannel=java.nio.channels.FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=lockChannel.lock()) {
                setPermissions(lockFile,"rw-------");
                if(!Files.exists(path)) {
                    var generator=KeyPairGenerator.getInstance("ECDSA","BC");generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));var generated=generator.generateKeyPair();
                    var properties=new Properties();properties.setProperty("public",Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey)generated.getPublic())));properties.setProperty("private",Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPrivateKey)generated.getPrivate())));
                    try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {setPermissions(path,"rw-------");properties.store(out,"CazaObjetos VAPID - private, do not commit");}
                    catch(FileAlreadyExistsException concurrent) { /* Another process initialized it. */ }
                }
                setPermissions(path,"rw-------");var properties=new Properties();try(var in=Files.newInputStream(path)){properties.load(in);}
                publicKey=properties.getProperty("public");pair=new KeyPair(Utils.loadPublicKey(publicKey),Utils.loadPrivateKey(properties.getProperty("private")));
                if(!Utils.verifyKeyPair(pair.getPrivate(),pair.getPublic()))throw new IllegalStateException("Par VAPID inválido");
                }
            } catch(Exception error) {throw new IllegalStateException("No se pudo cargar la clave Web Push persistente",error);}
        }
    }
    private void setPermissions(Path path,String value) throws java.io.IOException {if(Files.getFileStore(path).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(value));}
    public boolean enabled(){return enabled;}
    public String publicKey(){return publicKey;}
    public KeyPair pair(){return pair;}
}
