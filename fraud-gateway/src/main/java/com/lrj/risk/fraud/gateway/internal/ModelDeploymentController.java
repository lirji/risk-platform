package com.lrj.risk.fraud.gateway.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.lrj.risk.fraud.engine.ModelScorer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/model-deployments")
public class ModelDeploymentController {

    private final ModelScorer scorer;
    private final String allowedArtifactPrefix;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();

    public ModelDeploymentController(ModelScorer scorer,
            @Value("${risk.model.allowed-artifact-prefix:http://localhost:9000/}") String allowedArtifactPrefix) {
        this.scorer = scorer;
        this.allowedArtifactPrefix = allowedArtifactPrefix;
    }

    @PostMapping
    public ResponseEntity<Void> deploy(@Valid @RequestBody DeploymentRequest request) {
        if (!request.artifactUri().startsWith(allowedArtifactPrefix)) {
            throw new IllegalArgumentException("artifact URI is outside the allowed model repository");
        }
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(request.artifactUri()))
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("artifact HTTP " + response.statusCode());
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()));
            if (!MessageDigest.isEqual(checksum.getBytes(StandardCharsets.US_ASCII),
                    request.checksum().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("model checksum mismatch");
            }
            scorer.deploy(response.body(), request.version(), request.rolloutPercentage());
            return ResponseEntity.noContent().build();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model deployment interrupted", interrupted);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("model deployment failed", exception);
        }
    }

    public record DeploymentRequest(@NotBlank String version, @NotBlank String artifactUri,
                                    @NotBlank String checksum,
                                    @Min(0) @Max(100) int rolloutPercentage) { }
}
