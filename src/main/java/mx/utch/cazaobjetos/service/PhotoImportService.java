package mx.utch.cazaobjetos.service;

import java.io.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.*;

@Service
public class PhotoImportService {
    private final PhotoService photos;
    private final RemoteImageService remote;
    public PhotoImportService(PhotoService photos, RemoteImageService remote) {
        this.photos = photos;
        this.remote = remote;
    }
    public PhotoResponseDto importPhoto(UUID parent, UUID actor, ImportPhotoRequestDto request) {
        photos.checkUpload(false, parent, actor);
        // Network I/O happens outside the database transaction. Upload rechecks permission, status and quota.
        byte[] bytes = remote.download(request.url());
        return photos.upload(false, parent, actor, new ImportedFile(bytes), request.referenceImage());
    }
    private record ImportedFile(byte[] bytes) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "imported-image"; }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File file) throws IOException { java.nio.file.Files.write(file.toPath(), bytes); }
    }
}
