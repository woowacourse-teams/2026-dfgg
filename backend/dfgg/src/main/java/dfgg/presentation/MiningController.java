package dfgg.presentation;

import dfgg.application.mining.EmbeddingTrainingResult;
import dfgg.application.mining.MiningTriggerService;
import dfgg.application.mining.SequentialPatternMiningResult;
import dfgg.domain.embedding.TrainingConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/mining")
public class MiningController {

    private final MiningTriggerService miningTriggerService;

    public MiningController(MiningTriggerService miningTriggerService) {
        this.miningTriggerService = miningTriggerService;
    }

    @PostMapping("/embeddings")
    public ResponseEntity<EmbeddingTrainingResult> trainEmbeddings(
            @RequestParam @NotBlank String algorithmVersion,
            @RequestParam(defaultValue = "2.0") @DecimalMin("0.1") @DecimalMax("10.0") double winWeight,
            @RequestParam(defaultValue = "64") @Min(1) @Max(256) int dimensions,
            @RequestParam(defaultValue = "5") @Min(0) @Max(20) int negativeSamples,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int epochs,
            @RequestParam(defaultValue = "0.025") @DecimalMin("0.0001") @DecimalMax("1.0") double learningRate,
            @RequestParam(defaultValue = "42") long randomSeed
    ) {
        TrainingConfig config = new TrainingConfig(dimensions, negativeSamples, epochs, learningRate, randomSeed);
        return ResponseEntity.ok(miningTriggerService.trainEmbeddings(winWeight, config, algorithmVersion));
    }

    @PostMapping("/patterns")
    public ResponseEntity<SequentialPatternMiningResult> minePatterns(
            @RequestParam @NotBlank String algorithmVersion,
            @RequestParam(defaultValue = "RANKED_SOLO_5x5") String queueType,
            @RequestParam(defaultValue = "10") @Min(1) int minSupport
    ) {
        return ResponseEntity.ok(miningTriggerService.mineSequentialPatterns(queueType, minSupport, algorithmVersion));
    }
}
