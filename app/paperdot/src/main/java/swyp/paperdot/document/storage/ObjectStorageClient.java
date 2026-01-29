package swyp.paperdot.document.storage;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import swyp.paperdot.document.enums.StorageProvider;

public interface ObjectStorageClient {

    void upload(String key, MultipartFile file, String contentType) throws IOException;

    String getBucket();

    StorageProvider getProvider();
}
