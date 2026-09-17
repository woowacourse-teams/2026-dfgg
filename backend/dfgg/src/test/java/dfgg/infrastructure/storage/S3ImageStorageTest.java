package dfgg.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ImageStorageTest {
    private S3Client client;
    private S3ImageStorage storage;
    private final String key = "dfgg/images/16.15/champions/Aatrox.png";

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3ImageStorage(client, "assets");
    }

    @Test
    void 기존_파일이면_다운로드와_업로드를_생략한다() {
        storage.store(key, () -> { throw new AssertionError("must not download"); });
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 없는_파일은_PNG와_캐시_메타데이터로_업로드한다() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        byte[] image = {1, 2, 3};
        storage.store(key, () -> image);
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("assets");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().cacheControl()).contains("immutable");
        try (var stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(image);
        }
    }

    @Test
    void 권한_오류를_파일_없음으로_처리하지_않는다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());
        assertThatThrownBy(() -> storage.store(key, () -> { throw new AssertionError(); }))
                .isInstanceOf(S3Exception.class);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 업로드_실패를_전파한다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build());
        assertThatThrownBy(() -> storage.store(key, () -> new byte[]{1})).isInstanceOf(S3Exception.class);
    }

    @Test
    void 다운로드_실패시_업로드하지_않는다() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> storage.store(key, () -> { throw new IllegalStateException("download failed"); }))
                .hasMessage("download failed");
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 설정_누락은_AWS_호출_전에_실패한다() {
        storage = new S3ImageStorage(client, "");
        assertThatThrownBy(() -> storage.store(key, () -> new byte[]{1}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ASSETS_S3_BUCKET");
        verifyNoInteractions(client);
    }
}
