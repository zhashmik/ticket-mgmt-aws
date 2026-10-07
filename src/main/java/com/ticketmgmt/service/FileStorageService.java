package com.ticketmgmt.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Abstraction over file storage so the business layer is decoupled from AWS.
 * Backed by S3 under the {@code aws} profile and by local disk under {@code local}.
 */
public interface FileStorageService {

    String uploadAttachment(Long ticketId, MultipartFile file) throws IOException;

    String uploadPdf(Long ticketId, byte[] pdfBytes);

    String generateDownloadUrl(String key);

    void deleteAttachment(String key);
}
