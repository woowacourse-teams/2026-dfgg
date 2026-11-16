package dfgg.domain.match;

import java.util.List;

/** 어느 시각 직전의 참가자 한 명의 상태. 게임 중 클라이언트가 볼 수 있는 값만 담는다. */
public record ParticipantState(int level, int kills, int deaths, int assists, CoreItemInventory coreItems) {

    private static final int STARTING_LEVEL = 1;

    public static ParticipantState initial() {
        return new ParticipantState(STARTING_LEVEL, 0, 0, 0, CoreItemInventory.empty());
    }

    public ParticipantState killed() {
        return new ParticipantState(level, kills + 1, deaths, assists, coreItems);
    }

    public ParticipantState died() {
        return new ParticipantState(level, kills, deaths + 1, assists, coreItems);
    }

    public ParticipantState assisted() {
        return new ParticipantState(level, kills, deaths, assists + 1, coreItems);
    }

    /** 기록 순서가 어긋나도 레벨이 내려가지 않도록 더 높은 쪽을 남긴다. */
    public ParticipantState leveledTo(int reachedLevel) {
        return new ParticipantState(Math.max(level, reachedLevel), kills, deaths, assists, coreItems);
    }

    public ParticipantState coreItemsChanged(CoreItemChange change) {
        return new ParticipantState(level, kills, deaths, assists, coreItems.changed(change));
    }

    /** 그 시각에 들고 있던 Core를 얻은 순서대로 돌려준다. */
    public List<Integer> coreItemIds() {
        return coreItems.itemIds();
    }
}
