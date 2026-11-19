package dfgg.presentation.dto.request;

import dfgg.domain.champion.ChampionPosition;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

/**
 * 일반 게임은 아군 4명·적 5명과 모든 챔피언의 이름·포지션이 필수다.
 * 훈련모드·사용자 설정 게임은 내 챔피언 이름만 본다. 인원·포지션·팀원 이름은 정해져 있지 않다.
 */
public class TeamRosterValidator implements ConstraintValidator<ValidTeamRoster, NextItemRecommendationV4Request> {

    private static final int ALLY_COUNT = 4;
    private static final int ENEMY_COUNT = 5;
    private static final String NAME_REQUIRED = "챔피언 이름은 필수입니다";
    private static final String POSITION_REQUIRED = "포지션은 필수입니다";
    private static final String POSITION_FORMAT =
            "올바른 포지션 형식이 아닙니다 (TOP, JUNGLE, MID, BOTTOM, SUPPORT 중 하나)";

    @Override
    public boolean isValid(NextItemRecommendationV4Request request, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        boolean freeRoster = request.gameQueue().hasFreeRoster();
        boolean valid = isChampionValid(request.myChampion(), true, freeRoster,
                (message, property) -> addViolation(context, message, "myChampion", property));
        valid &= isTeamValid(request.allies(), "allies", ALLY_COUNT, "아군", freeRoster, context);
        valid &= isTeamValid(request.enemies(), "enemies", ENEMY_COUNT, "적군", freeRoster, context);
        return valid;
    }

    private boolean isTeamValid(List<ChampionDto> team, String field, int count, String label, boolean freeRoster,
            ConstraintValidatorContext context) {
        if (team == null) {
            return true;
        }
        boolean valid = isSizeValid(team.size(), field, count, label, freeRoster, context);
        for (int index = 0; index < team.size(); index++) {
            int memberIndex = index;
            valid &= isChampionValid(team.get(index), !freeRoster, freeRoster,
                    (message, property) -> addMemberViolation(context, message, field, memberIndex, property));
        }
        return valid;
    }

    private boolean isSizeValid(int size, String field, int count, String label, boolean freeRoster,
            ConstraintValidatorContext context) {
        if (freeRoster || size == count) {
            return true;
        }
        context.buildConstraintViolationWithTemplate(label + "은 " + count + "명이어야 합니다")
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }

    private boolean isChampionValid(ChampionDto champion, boolean nameRequired, boolean freeRoster,
            ViolationTarget target) {
        if (champion == null) {
            return true;
        }
        boolean valid = true;
        if (nameRequired && isBlank(champion.name())) {
            target.add(NAME_REQUIRED, "name");
            valid = false;
        }
        String positionViolation = positionViolationOf(champion.position(), freeRoster);
        if (positionViolation != null) {
            target.add(positionViolation, "position");
            valid = false;
        }
        return valid;
    }

    /** 위반이 없으면 null이다. */
    private String positionViolationOf(String position, boolean freeRoster) {
        if (freeRoster) {
            return null;
        }
        if (isBlank(position)) {
            return POSITION_REQUIRED;
        }
        if (!ChampionPosition.isName(position)) {
            return POSITION_FORMAT;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addViolation(ConstraintValidatorContext context, String message, String field, String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addPropertyNode(property)
                .addConstraintViolation();
    }

    private void addMemberViolation(ConstraintValidatorContext context, String message, String field, int index,
            String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addPropertyNode(property).inIterable().atIndex(index)
                .addConstraintViolation();
    }

    /** 챔피언 하나의 위반을 붙일 경로. 내 챔피언과 팀원의 경로 모양이 다르다. */
    @FunctionalInterface
    private interface ViolationTarget {
        void add(String message, String property);
    }
}
