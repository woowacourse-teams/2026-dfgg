/** /lol-match-history 원본 응답. 추려서 내보내므로 main 밖으로 나가지 않는다. */
export type LcuMatchHistory = {
  // games 가 두 겹이다. 바깥은 페이징 정보, 안쪽이 실제 배열.
  games: { games: LcuGame[] };
};

export type LcuGame = {
  gameId: number;
  queueId: number;
  /** 'CLASSIC' | 'ARAM' | 'KIWI'(무작위 총력전: 아수라장) 등 */
  gameMode: string;
  /** 사용자 설정 게임이면 'CUSTOM_GAME'. 큐 테이블의 type 과 값이 어긋나므로 이쪽을 믿는다. */
  gameType: string;
  /** 초 */
  gameDuration: number;
  gameCreationDate: string;
  /**
   * 10명이 아니라 '나' 하나만 온다. 20경기 전부 길이 1인 것을 확인했다.
   * 10명 명단이 필요하면 /lol-match-history/v1/games/{gameId} 를 따로 불러야 한다.
   */
  participants: LcuParticipant[];
  /** participants 와 마찬가지로 나 하나뿐이다. */
  participantIdentities: LcuParticipantIdentity[];
  /** 팀별 승패와 오브젝트. 목록 응답에도 들어 있다. */
  teams: LcuTeam[];
};

export type LcuTeam = {
  /** 100 = 블루, 200 = 레드 */
  teamId: number;
  /** stats.win 은 불린인데 여기는 'Win' | 'Fail' 문자열이다. */
  win: string;
};

export type LcuParticipant = {
  /** 인게임 슬롯 번호(1~10). 배열에 나만 있어도 5 처럼 들어온다. */
  participantId: number;
  teamId: number;
  championId: number;
  spell1Id: number;
  spell2Id: number;
  stats: LcuParticipantStats;
};

export type LcuParticipantStats = {
  /** teams[].win 은 'Win' | 'Fail' 문자열이지만 여기는 불린이다. */
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  /** 게임이 끝났을 때의 챔피언 레벨 */
  champLevel: number;
  totalMinionsKilled: number;
  neutralMinionsKilled: number;
  goldEarned: number;
  /** 챔피언에게 넣은 피해량. 오브젝트 딜은 빠져 있다. */
  totalDamageDealtToChampions: number;
  totalDamageTaken: number;
  visionScore: number;
  wardsPlaced: number;
  wardsKilled: number;
  item0: number;
  item1: number;
  item2: number;
  item3: number;
  item4: number;
  item5: number;
  /** 장신구 */
  item6: number;
};

export type LcuParticipantIdentity = {
  participantId: number;
  /** accountId 는 0 으로 마스킹돼 온다. 사람을 가리려면 puuid 를 쓴다. */
  player: { gameName: string; tagLine: string; puuid: string };
};
