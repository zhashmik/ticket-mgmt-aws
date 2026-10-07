package com.ticketmgmt.service.aws;

import com.ticketmgmt.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("aws")
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry-minutes}")
    private int presignedUrlExpiryMinutes;

    public S3FileStorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public String uploadAttachment(Long ticketId, MultipartFile file) throws IOException {
        String key = String.format("attachments/%d/%s_%s",
                ticketId, UUID.randomUUID(), file.getOriginalFilename());
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName).key(key)
                .contentType(file.getContentType())
                .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                .metadata(Map.of("ticket-id", ticketId.toString(),
                                 "original-filename", file.getOriginalFilename()))
                .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return key;
    }

    public String uploadPdf(Long ticketId, byte[] pdfBytes) {
        String key = String.format("generated-pdfs/%d/resolution-%d.pdf",
                ticketId, System.currentTimeMillis());
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName).key(key)
                .contentType("application/pdf")
                .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                .build(), RequestBody.fromBytes(pdfBytes));
        return key;
    }

    public String generateDownloadUrl(String s3Key) {
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .getObjectRequest(b -> b.bucket(bucketName).key(s3Key))
                .build()).url().toString();
    }

    public String generateUploadUrl(Long ticketId, String filename, String contentType) {
        String key = String.format("attachments/%d/%s_%s", ticketId, UUID.randomUUID(), filename);
        return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .putObjectRequest(b -> b.bucket(bucketName).key(key).contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AWS_KMS))
                .build()).url().toString();
    }

    public void deleteAttachment(String s3Key) {
        s3Client.deleteObject(b -> b.bucket(bucketName).key(s3Key));
    }
}
