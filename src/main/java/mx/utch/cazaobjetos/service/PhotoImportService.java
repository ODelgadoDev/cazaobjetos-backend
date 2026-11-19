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
        return importPhoto(false,parent,actor,request);
    }
    public PhotoResponseDto importPhoto(boolean found, UUID parent, UUID actor, ImportPhotoRequestDto request) {
        photos.checkUpload(found,parent,actor);
        // Upload rechecks access and quota after downloading.
        return photos.upload(found,parent,actor,attachment(null,request.url()),request.referenceImage());
    }
    public MultipartFile attachment(MultipartFile file, String url) {
        if (url == null || url.isBlank()) return file;
        mx.utch.cazaobjetos.util.ValidateUtil.require(file == null,org.springframework.http.HttpStatus.BAD_REQUEST,
            "Elige un archivo o un enlace");
        return link(url);
    }
    public MultipartFile link(String url) {
        mx.utch.cazaobjetos.util.ValidateUtil.require(url!=null&&!url.isBlank()&&url.length()<=2048,
            org.springframework.http.HttpStatus.BAD_REQUEST,"Escribe un enlace de imagen válido");
        return new ImportedFile(remote.download(url));
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
