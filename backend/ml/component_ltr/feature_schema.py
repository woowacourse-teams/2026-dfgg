"""Java와 Python이 공유하는 Feature 이름·순서·버전 계약. 계산은 Java에서 수행한다."""
import hashlib
import json

SCHEMA_VERSION = "component-bundle-validation-v7"
FEATURE_NAMES = [
    "additional_cost",
    "purchase_quantity",
    "distinct_purchase_items",
    "slots_before",
    "slots_after",
    "completes_target_core",
    "consumed_owned_quantity",
    "consumed_owned_value",
    "basic_purchase_quantity",
    "intermediate_purchase_quantity",
    "attack_damage_delta",
    "ability_power_delta",
    "health_delta",
    "mana_delta",
    "armor_delta",
    "magic_resist_delta",
    "attack_speed_delta",
    "crit_chance_delta",
    "life_steal_delta",
    "flat_move_speed_delta",
    "percent_move_speed_delta",
    "health_regen_delta",
    "game_time_minutes",
    "position_top",
    "position_jungle",
    "position_mid",
    "position_bottom",
    "position_support",
    "position_unknown",
    "attack_damage_before",
    "ability_power_before",
    "health_before",
    "mana_before",
    "armor_before",
    "magic_resist_before",
    "attack_speed_before",
    "crit_chance_before",
    "life_steal_before",
    "flat_move_speed_before",
    "percent_move_speed_before",
    "health_regen_before",
    "target_remaining_gold_after_purchase",
    "target_remaining_recipe_depth",
    "contains_direct_target_material",
    "target_material_coverage_after_purchase",
    "target_progress_gain",
    "enemy_tank_count",
    "enemy_mage_count",
    "enemy_marksman_count",
    "enemy_assassin_count",
    "enemy_fighter_count",
    "ally_tank_count",
    "ally_mage_count",
    "ally_marksman_count",
    "ally_support_count",
    "is_preparation_candidate",
    "preparation_observation_rate",
    "preparation_support_log",
    "preparation_future_recipe_link_rate",
]

# 열 순서가 바뀌면 지문도 바뀌므로 다른 계약의 모델을 잘못 읽지 못하게 한다.
SCHEMA_FINGERPRINT = hashlib.sha256(
    json.dumps([SCHEMA_VERSION, FEATURE_NAMES]).encode()
).hexdigest()

# 비교 실험에서 게임 시간·포지션 열만 비활성화하는 경계다.
CONTEXT_FEATURE_START = FEATURE_NAMES.index("game_time_minutes")

CONTEXT_FEATURE_END = FEATURE_NAMES.index("attack_damage_before")

COMPOSITION_FEATURE_START = FEATURE_NAMES.index("enemy_tank_count")

PREPARATION_FEATURE_START = FEATURE_NAMES.index("is_preparation_candidate")
COMPOSITION_FEATURE_END = PREPARATION_FEATURE_START
