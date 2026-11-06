package dfgg.infrastructure.storage;

import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3ImageStorage {
    private final S3Client client;
    private final String bucket;

    public S3ImageStorage(@Lazy S3Client client,
                          @Value("${assets.s3.bucket:}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public void store(String key, Supplier<byte[]> content) {
        validateConfiguration();
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType("image/png")
                            .cacheControl("public,max-age=31536000,immutable")
                            .build(),
                    RequestBody.fromBytes(content.get()));
        }
    }

    private void validateConfiguration() {
        if (bucket.isBlank()) {
            throw new IllegalStateException("이미지 수집을 위해 ASSETS_S3_BUCKET을 설정해야 합니다.");
        }
    }
}
