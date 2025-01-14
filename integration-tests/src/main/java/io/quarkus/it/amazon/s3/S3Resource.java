package io.quarkus.it.amazon.s3;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.jboss.logging.Logger;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Path("/s3")
public class S3Resource {
    private static final String SYNC_BUCKET = "sync-" + UUID.randomUUID().toString();
    private static final String ASYNC_BUCKET = "async-" + UUID.randomUUID().toString();
    private static final String SAMPLE_S3_OBJECT = "sample S3 object";

    private static final Logger LOG = Logger.getLogger(S3Resource.class);

    @Inject
    S3Client s3Client;

    @Inject
    S3AsyncClient s3AsyncClient;

    @Inject
    S3Presigner s3Presigner;

    @GET
    @Path("async")
    @Produces(TEXT_PLAIN)
    public CompletionStage<String> testAsyncS3() {
        LOG.info("Testing Async S3 client with bucket: " + ASYNC_BUCKET);
        String keyValue = UUID.randomUUID().toString();

        return S3Utils.createBucketAsync(s3AsyncClient, ASYNC_BUCKET)
                .thenCompose(bucket -> s3AsyncClient.putObject(S3Utils.createPutRequest(ASYNC_BUCKET, keyValue),
                        AsyncRequestBody.fromString(SAMPLE_S3_OBJECT)))
                .thenCompose(resp -> s3AsyncClient.getObject(S3Utils.createGetRequest(ASYNC_BUCKET, keyValue),
                        AsyncResponseTransformer.toBytes()))
                .thenApply(resp -> metadata(resp.response()) + "+" + resp.asUtf8String())
                .exceptionally(th -> {
                    LOG.error("Error during async S3 operations", th.getCause());
                    return "ERROR";
                });
    }

    @GET
    @Path("blocking")
    @Produces(TEXT_PLAIN)
    public String testBlockingS3() {
        LOG.info("Testing S3 Blocking client with bucket: " + SYNC_BUCKET);

        String keyValue = UUID.randomUUID().toString();
        String result = null;

        try {
            if (S3Utils.createBucket(s3Client, SYNC_BUCKET)) {
                if (s3Client.putObject(S3Utils.createPutRequest(SYNC_BUCKET, keyValue),
                        RequestBody.fromString(SAMPLE_S3_OBJECT)) != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();

                    GetObjectResponse object = s3Client.getObject(S3Utils.createGetRequest(SYNC_BUCKET, keyValue),
                            ResponseTransformer.toOutputStream(baos));

                    if (object != null) {
                        result = metadata(object) + "+" + baos.toString();
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Error during S3 operations.", ex);
            return "ERROR";
        }
        return result;
    }

    @GET
    @Path("presign")
    @Produces(TEXT_PLAIN)
    public String testPresigner() {
        LOG.info("Testing S3 presigner with bucket: " + SYNC_BUCKET);

        String keyValue = UUID.randomUUID().toString();
        String result = null;

        try {
            result = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .getObjectRequest(S3Utils.createGetRequest(SYNC_BUCKET, keyValue))
                    .signatureDuration(Duration.ofSeconds(30))
                    .build())
                    .url()
                    .toString();
        } catch (Exception ex) {
            LOG.error("Error during S3 operations.", ex);
            return "ERROR";
        }
        return result;
    }

    private String metadata(GetObjectResponse objectResponse) {
        return objectResponse.metadata().get(S3ModifyResponse.CUSTOM_METADATA);
    }

}
