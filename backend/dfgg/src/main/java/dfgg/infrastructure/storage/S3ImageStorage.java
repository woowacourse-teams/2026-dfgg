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

    /** 객체가 있으면 참, 404면 거짓이다. 권한 오류(403)는 없음으로 보지 않고 그대로 던진다. */
    public boolean exists(String key) {
        validateConfiguration();
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            return false;
        }
    }

    /** 작은 표시 파일을 덮어쓴다. 그림과 달리 바뀔 수 있어 캐시하지 않는다. */
    public void mark(String key, String content) {
        validateConfiguration();
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType("text/plain")
                        .cacheControl("no-cache")
                        .build(),
                RequestBody.fromString(content));
    }

    private void validateConfiguration() {
        if (bucket.isBlank()) {
            throw new IllegalStateException("이미지 수집을 위해 ASSETS_S3_BUCKET을 설정해야 합니다.");
        }
    }
}
