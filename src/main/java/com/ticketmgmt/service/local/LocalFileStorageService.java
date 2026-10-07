package com.ticketmgmt.service.local;

import com.ticketmgmt.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Local disk implementation of {@link FileStorageService} for development.
 * Files are stored under {@code ./local-storage}.
 */
@Service
@Profile("local")
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Path ROOT = Paths.get("local-storage");

    @Override
    public String uploadAttachment(Long ticketId, MultipartFile file) throws IOException {
        String key = String.format("attachments/%d/%s_%s",
                ticketId, UUID.randomUUID(), file.getOriginalFilename());
        write(key, file.getBytes());
        return key;
    }

    @Override
    public String uploadPdf(Long ticketId, byte[] pdfBytes) {
        String key = String.format("generated-pdfs/%d/resolution-%d.pdf",
                ticketId, System.currentTimeMillis());
        try {
            write(key, pdfBytes);
        } catch (IOException e) {
            log.error("Failed to write local PDF {}: {}", key, e.getMessage());
        }
        return key;
    }

    @Override
    public String generateDownloadUrl(String key) {
        return "file://" + ROOT.resolve(key).toAbsolutePath();
    }

    @Override
    public void deleteAttachment(String key) {
        try {
            Files.deleteIfExists(ROOT.resolve(key));
        } catch (IOException e) {
            log.warn("Failed to delete local file {}: {}", key, e.getMessage());
        }
    }

    private void write(String key, byte[] bytes) throws IOException {
        Path target = ROOT.resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        log.info("Stored local file: {}", target);
    }
}
