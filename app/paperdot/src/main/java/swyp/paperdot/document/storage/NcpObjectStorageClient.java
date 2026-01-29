package swyp.paperdot.document.storage;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import swyp.paperdot.document.enums.StorageProvider;

public class NcpObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3Client;
    private final NcpStorageProperties properties;

    public NcpObjectStorageClient(S3Client s3Client, NcpStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void upload(String key, MultipartFile file, String contentType) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    @Override
    public String getBucket() {
        return properties.getBucket();
    }

    @Override
    public StorageProvider getProvider() {
        return StorageProvider.NCLOUD;
    }
}
