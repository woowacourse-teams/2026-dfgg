package dfgg.presentation;

import dfgg.application.itemstats.ItemStatsAggregationResult;
import dfgg.application.itemstats.ItemStatsAggregationService;
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
    private final ItemStatsAggregationService itemStatsAggregationService;

    public MiningController(
            MiningTriggerService miningTriggerService,
            ItemStatsAggregationService itemStatsAggregationService
    ) {
        this.miningTriggerService = miningTriggerService;
        this.itemStatsAggregationService = itemStatsAggregationService;
    }

    /**
     * 4개 generator가 읽을 통계 테이블을 원본 참가자 데이터에서 전량 재계산한다. 멱등하다.
     *
     * <p>{@code recentPatchWindowSize}는 최신 몇 개 패치를 "최근"으로 볼지다. 기본 3(약 6주) —
     * 실측상 최근 3패치가 현재 패치 대비 오차를 절반(0.302pp → 0.164pp)으로 줄이면서
     * 삼중항 지지도의 89%를 유지한다.
     */
    @PostMapping("/item-stats")
    public ResponseEntity<ItemStatsAggregationResult> aggregateItemStats(
            @RequestParam(defaultValue = "3") @Min(1) @Max(20) int recentPatchWindowSize
    ) {
        return ResponseEntity.ok(itemStatsAggregationService.aggregate(recentPatchWindowSize));
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

    @PostMapping("/counter-embeddings")
    public ResponseEntity<EmbeddingTrainingResult> trainCounterEmbeddings(
            @RequestParam @NotBlank String algorithmVersion,
            @RequestParam(defaultValue = "2.0") @DecimalMin("0.1") @DecimalMax("10.0") double winWeight,
            @RequestParam(defaultValue = "64") @Min(1) @Max(256) int dimensions,
            @RequestParam(defaultValue = "5") @Min(0) @Max(20) int negativeSamples,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int epochs,
            @RequestParam(defaultValue = "0.025") @DecimalMin("0.0001") @DecimalMax("1.0") double learningRate,
            @RequestParam(defaultValue = "42") long randomSeed
    ) {
        TrainingConfig config = new TrainingConfig(dimensions, negativeSamples, epochs, learningRate, randomSeed);
        return ResponseEntity.ok(miningTriggerService.trainCounterEmbeddings(winWeight, config, algorithmVersion));
    }

    @PostMapping("/patterns")
    public ResponseEntity<SequentialPatternMiningResult> minePatterns(
            @RequestParam @NotBlank String algorithmVersion,
            @RequestParam(defaultValue = "10") @Min(1) int minSupport
    ) {
        return ResponseEntity.ok(miningTriggerService.mineSequentialPatterns(minSupport, algorithmVersion));
    }
}
