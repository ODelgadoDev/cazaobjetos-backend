package mx.utch.cazaobjetos.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.exception.BusinessException;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

public final class AvatarImageUtil {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private AvatarImageUtil() {}

    public static byte[] normalize(MultipartFile file) {
        require(!file.isEmpty(), HttpStatus.BAD_REQUEST, "Selecciona una foto de perfil");
        require(file.getSize() <= MAX_BYTES, HttpStatus.PAYLOAD_TOO_LARGE, "La foto excede 5 MB");
        try (var raw = file.getInputStream(); var input = new MemoryCacheImageInputStream(raw)) {
            var readers = ImageIO.getImageReaders(input);
            require(readers.hasNext(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se admiten imágenes JPEG o PNG");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                require(Set.of("jpeg", "jpg", "png").contains(format), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se admiten imágenes JPEG o PNG");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                require(width > 0 && height > 0 && (long) width * height <= 12_000_000 && width <= 6000 && height <= 6000,
                        HttpStatus.BAD_REQUEST, "Máximo 12 megapíxeles y 6000 píxeles por lado");
                var image = reader.read(0);
                var avatar = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
                var graphics = avatar.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 512, 512);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(image, 0, 0, 512, 512, x, y, x + side, y + side, null);
                } finally { graphics.dispose(); }
                // Only decoded pixels are retained; original metadata and appended data are discarded.
                var output = new ByteArrayOutputStream();
                require(ImageIO.write(avatar, "jpeg", output), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Imagen no compatible");
                require(output.size() <= MAX_BYTES, HttpStatus.PAYLOAD_TOO_LARGE, "La imagen procesada excede 5 MB");
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La imagen está dañada o no es válida");
        }
    }
}
