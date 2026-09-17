package dfgg.infrastructure.storage;

import java.net.URI;
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
    private final String publicBaseUrl;

    public S3ImageStorage(@Lazy S3Client client,
                          @Value("${assets.s3.bucket:}") String bucket,
                          @Value("${assets.s3.public-base-url:}") String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public String store(String key, Supplier<byte[]> content) {
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
        return publicBaseUrl + "/" + key;
    }

    private void validateConfiguration() {
        if (bucket.isBlank() || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("이미지 수집을 위해 ASSETS_S3_BUCKET과 ASSETS_PUBLIC_BASE_URL을 설정해야 합니다.");
        }
        URI uri = URI.create(publicBaseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalStateException("ASSETS_PUBLIC_BASE_URL은 쿼리, 프래그먼트, 사용자 정보가 없는 HTTPS 기본 주소여야 합니다.");
        }
    }
}
