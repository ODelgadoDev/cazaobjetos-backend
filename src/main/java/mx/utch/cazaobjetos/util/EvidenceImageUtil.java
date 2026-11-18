package mx.utch.cazaobjetos.util;

import java.io.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.exception.BusinessException;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

public final class EvidenceImageUtil {
    private EvidenceImageUtil() {}
    public record Image(String type,byte[] bytes,String hash) {}
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public static Image normalize(MultipartFile file) {
        require(!file.isEmpty(),HttpStatus.BAD_REQUEST,"Selecciona una imagen de evidencia");
        require(file.getSize()<=5*1024*1024,HttpStatus.PAYLOAD_TOO_LARGE,"La evidencia excede 5 MB");
        try {
            byte[] bytes=file.getBytes();
            try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                require(readers.hasNext(),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Solo se admiten evidencias JPEG o PNG");
                var reader=readers.next();
                try {
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    require(Set.of("jpeg","jpg","png").contains(format),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Solo se admiten evidencias JPEG o PNG");
                    reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                    require(width>0&&height>0&&(long)width*height<=12_000_000&&width<=6000&&height<=6000,HttpStatus.BAD_REQUEST,"Máximo 12 megapíxeles y 6000 píxeles por lado");
                    var decoded=reader.read(0);var output=new ByteArrayOutputStream();
                    require(ImageIO.write(decoded,format,output),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Imagen no compatible");
                    require(output.size()<=5*1024*1024,HttpStatus.PAYLOAD_TOO_LARGE,"La evidencia procesada excede 5 MB");
                    return new Image(format.equals("png")?"image/png":"image/jpeg",output.toByteArray(),hash(bytes));
                } finally { reader.dispose(); }
            }
        } catch(IOException|IllegalArgumentException error) { throw new BusinessException(HttpStatus.BAD_REQUEST,"La imagen está dañada o no es válida"); }
    }
}
