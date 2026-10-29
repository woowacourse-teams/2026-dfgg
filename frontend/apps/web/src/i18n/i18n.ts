import META from './meta.json';

export type Locale = 'ko' | 'en';

export function pickLocale(languages: readonly string[]): Locale {
  for (const tag of languages) {
    const base = tag.toLowerCase().split('-')[0];
    if (base === 'ko') return 'ko';
    if (base === 'en') return 'en';
  }
  return 'en';
}

const KO = {
  // 빌드 때 언어별 index.html 에도 넣어야 해서 webpack.config.js 와 같은 파일을 쓴다.
  meta: META.ko,
  header: {
    navLabel: '주요 메뉴',
    homeLabel: '홈',
    skipToContent: '본문으로 건너뛰기',
    installGuide: '설치 안내',
    features: '기능 소개',
    download: '다운로드',
    switchLanguage: 'EN',
    // 화면에 보이는 글자(EN)를 이름 안에 그대로 넣어야 음성 입력 사용자도 부를 수 있다.
    switchLanguageLabel: 'EN, English',
  },
  download: {
    store: 'Microsoft Store에서 무료로 받기',
    exe: '설치 파일(.exe) 받기',
    requirement: 'Windows 10·11',
    stats: [
      { value: '1K+', label: '누적 다운로드' },
      { value: '5.0', label: '사용자 평점' },
      { value: '0원', label: '완전 무료' },
    ],
  },
  hero: {
    chatHistory: { name: '탑블레이드가렌', champion: '레넥톤', text: '원딜 템 뭐갈거?' },
    chatTyped: { name: '원딜장인', champion: '징크스', text: '템 뭐 가야됨?' },
    headline: ['이젠 물어보지 마세요'],
    subtitle: {
      prefix: 'DFGG가 ',
      teamComp: '양 팀 조합',
      joiner: '과 ',
      liveState: '실시간 상황',
      suffix: '에 맞춰',
      secondLinePrefix: '',
      coreItem: '코어템',
      secondLineSuffix: '을 추천해줘요!',
    },
  },
  demo: {
    heading: ['게임 중엔', '이렇게 뜹니다!'],
    stepsLabel: '데모 단계',
    overlayLabel: '게임 화면 위에 뜨는 DFGG 오버레이 예시',
    pauseVideo: '배경 영상 멈추기',
    playVideo: '배경 영상 재생하기',
    stages: [
      {
        label: '게임 시작',
        title: '게임 안에서 바로 보이게!',
        body: '게임이 시작되면 1~3순위 코어와 2개의 대안 템이 화면에 뜹니다.',
      },
      {
        label: '아이템 구매',
        title: '템을 사면 알아서 다음 코어 추천!',
        body: '하나 살 때마다 추천이 새로 갱신됩니다.',
      },
      {
        label: '추천 이유',
        title: '왜 이 템을 추천할까? 설명까지!',
        body: '누구를 상대하기 좋은지,\n어떤 아군과 잘 맞는지까지 확인할 수 있어요.',
      },
      {
        label: '풀템 완성',
        title: '이제 시작해볼까요?!',
        body: '추천을 따라 하나씩 사다 보면 여섯 칸이 채워집니다.',
      },
    ],
  },
  overlay: {
    coreTitle: (core: number) => `${core}코어 추천`,
    synergy: '시너지',
    counter: '카운터',
    allyRelation: '아군 — 시너지 좋음',
    counterRelation: '상대 — 카운터로 좋음',
    rank: (n: number) => `추천 ${n}순위`,
    progress: (total: number, done: number) => `아이템 ${total}칸 중 ${done}칸 구매`,
    nowBuy: '지금 살 것',
    shortage: (gold: string) => `${gold} 부족`,
    situational: '상대 조합 대응',
    expand: '오버레이 펼치기',
    collapse: '오버레이 접기',
    inventoryLabel: (count: number) => `인벤토리 ${count}칸 채워짐`,
  },
  install: {
    title: '파란 경고창이 떠도 괜찮습니다',
    highlight: '아직 준비 중이라 뜨는 경고예요!',
    body: [
      'exe로 받으면 Windows가 알 수 없는 게시자라며 한 번 막습니다.',
      '코드 서명 인증서가 아직 없어서 그래요.',
    ],
    followMobile: '아래 창처럼 누르면 설치됩니다.',
    followDesktop: '옆 창처럼 누르면 설치됩니다.',
    store: {
      prefix: '경고 없이 설치하려면 ',
      link: 'Microsoft Store 버전',
      suffix: '을 받으세요.',
    },
    faqTitle: '자주 묻는 질문',
    faq: [
      {
        q: '오버레이가 안 보여요',
        a: '롤이 전체 화면이면 오버레이가 가려집니다. DFGG가 게임 밖에서 테두리 없음 모드로 바꿔두지만(원래 설정은 백업), 그 뒤에 다시 바꿨다면 설정 → 그래픽 → 창 모드를 테두리 없음으로 맞춰주세요.',
      },
      {
        q: '추천이 안 떠요',
        a: '게임이 시작돼야 뜹니다. 대기실이나 챔피언 선택 중에는 나오지 않고, 롤 클라이언트가 켜져 있어야 연결됩니다.',
      },
      {
        q: '다운로드가 막혀요',
        a: '브라우저 다운로드 목록에서 계속 또는 유지를 누르세요. 파일은 DFGG GitHub 릴리스에서 받습니다.',
      },
      {
        q: 'Mac에서도 되나요?',
        a: '지금은 Windows만 지원합니다.',
      },
    ],
  },
  smartScreen: {
    title: 'Windows의 PC 보호',
    body: 'Microsoft Defender SmartScreen에서 인식할 수 없는 앱의 시작을 차단했습니다. 이 앱을 실행하면 PC가 위험에 노출될 수 있습니다.',
    moreInfo: '추가 정보',
    app: '앱:',
    publisher: '게시자:',
    unknownPublisher: '알 수 없는 게시자',
    run: '실행',
    dontRun: '실행 안 함',
    stepMoreInfo: '1. 추가 정보를 눌러보세요',
    stepRun: '2. 실행을 누르면 설치가 시작됩니다',
    done: '설치가 시작됩니다',
    replay: '다시 보기',
  },
  features: {
    title: '기능 소개 - DFGG',
    kicker: 'DFGG 둘러보기',
    headline: ['한 판의 시작부터', '끝까지 옆에서'],
    intro:
      '챔피언을 고를 때, 게임 중에, 끝난 뒤까지.\nDFGG가 하는 일을 실제 앱 화면으로 보여 드릴게요.',
    stepsLabel: '한 판의 흐름',
    steps: [
      {
        key: 'champ-select',
        label: '밴픽',
        title: '고르기 전에, 프로의 선택부터',
        body: '밴픽이 시작되면 DFGG 창이 밴픽 화면으로 바뀌어요.\n같은 포지션 프로들이 최근 대회에서 무엇을 골랐는지,\n상대 챔피언은 누구로 받아칠지 한 화면에서 봐요.',
        points: [
          { name: '오늘의 픽', text: '최근 대회의 프로 픽과\n그 선수가 쓴 룬·아이템' },
          {
            name: '카운터 추천',
            text: '상대가 챔피언을 공개할 때마다\n맞라인 상대와 받아칠 챔피언',
          },
          { name: '룬 자동 적용', text: '챔피언을 고르면\n추천 룬이 클라이언트에 바로 들어가요' },
        ],
        imageAlt: 'DFGG 밴픽 화면. 오늘의 픽과 카운터 추천이 보인다',
        subImageAlt: 'DFGG 룬 추천 화면',
      },
      {
        key: 'in-game',
        label: '게임 중',
        title: '게임 화면 위에서, 지금 살 템까지',
        body: '게임이 시작되면 화면 왼쪽에 작은 창이 떠요.\n양 팀 조합과 지금 가진 아이템을 보고 다음 코어를 골라 주고,\n템을 살 때마다 새로 바꿔요.',
        points: [
          {
            name: '코어 1~3순위',
            text: '아군과 잘 맞는 이유, 상대를 받아치는 이유를\n챔피언 얼굴로 함께',
          },
          { name: '지금 살 것', text: '들고 있는 골드로\n지금 바로 살 수 있는 재료 묶음' },
          { name: '상대 조합 대응', text: '치유 감소처럼\n상대 때문에 필요한 아이템 최대 2개' },
        ],
        imageAlt: '게임 화면 왼쪽에 뜬 DFGG 아이템 추천 오버레이',
      },
      {
        key: 'post-game',
        label: '게임 후',
        title: '끝나면, 내가 몇 등이었는지',
        body: '게임이 끝나면 10명의 플레이를 같은 기준으로 매긴\nDF 스코어가 나와요.\n킬만이 아니라 피해량, 시야, 오브젝트까지 함께 봐서\n1등은 MVP로 불러 드려요.',
        points: [{ name: 'DF 스코어', text: '이번 판 10명 중 내 순위와 점수' }],
        imageAlt: '방금 끝난 판의 DF 스코어 MVP 카드',
        subImageAlt: '10명의 DF 스코어 순위',
      },
      {
        key: 'highlight',
        label: '하이라이트',
        title: '내 활약은 알아서 남아요',
        body: '녹화 버튼을 누를 필요가 없어요.\n내가 킬을 따거나 용·바론을 잡은 장면을\n싸움이 시작된 순간부터 클립으로 잘라 둬요.',
        points: [
          { name: '자동 녹화', text: '게임이 시작되면 알아서 녹화하고\n끝나면 바로 클립으로 정리' },
          { name: '이어 보기', text: '한 클립이 끝나면\n다음 장면으로 넘어가요' },
          {
            name: '전적에서 다시',
            text: '전적 카드의 하이라이트 버튼으로\n그 판 장면을 언제든 다시 보기',
          },
        ],
        imageAlt: '방금 끝난 판의 하이라이트 클립 재생 화면',
        subImageAlt: '전적 카드에서 연 하이라이트 클립',
      },
      {
        key: 'history',
        label: '전적',
        title: '내 전적을 한눈에',
        body: '롤 클라이언트 옆에 붙어서 최근 전적을 보여 줘요.\n한 판을 펼치면 10명의 피해량과 아이템,\nDF 순위를 맞대어 비교할 수 있어요.',
        points: [
          { name: '최근 요약', text: '승률, KDA, 모스트 챔피언, 최고의 한 판' },
          { name: '맞대결 비교', text: '같은 라인끼리 나란히 놓고\n피해량과 아이템 비교' },
        ],
        imageAlt: 'DFGG 홈 화면. 최근 전적 요약',
        subImageAlt: '펼친 전적. 10명의 피해량 비교',
      },
    ],
    tech: {
      kicker: '추천을 믿을 수 있는 이유',
      title: ['감이 아니라', '실제 게임 기록으로'],
      intro:
        '“이 챔피언은 보통 이걸 가요”에서 끝내지 않으려고,\n실제 한국 랭크 게임에서 사람들이 무엇을 샀는지부터 모았어요.',
      data: {
        title: '쉬지 않고 모으는 한국 랭크 게임',
        body: '한국 솔로 랭크 경기를 사람 손 없이 계속 모아요.\n같은 경기가 여러 사람의 전적에 겹쳐 들어와도\n한 번만 세요.',
        stats: [
          { value: '약 47만', unit: '판', label: '모아 온 솔로 랭크 경기' },
          { value: '약 2,600만', unit: '건', label: '그 안의 아이템 구매 기록' },
        ],
        note: '숫자는 반올림한 값이에요.',
      },
      model: {
        title: '머신러닝으로 만든 추천 AI',
        body: '실제 한국 랭크 게임의 구매 기록으로\n머신러닝 모델을 학습시켰어요.\n그 상황에서 실제 플레이어들이 다음에 산 아이템이\n위로 오도록 순서를 매기는 법을 배운 모델이에요.',
        sourcesLabel: '후보를 모으는 네 갈래',
        sources: [
          '지금 빌드를 이어 가는 템',
          '이 챔피언이 즐겨 사는 템',
          '아군과 궁합이 좋은 템',
          '상대를 받아치는 템',
        ],
        rankedLabel: 'AI 모델이 매긴 순서',
        note: 'AD 르블랑처럼 흔치 않은 빌드도 지우지 않아요.\n게임 규칙상 살 수 없는 아이템만 빼요.',
      },
      reason: {
        title: '추천마다 붙는 이유',
        body: '점수만 보여 주면 왜 그 아이템인지 알 수 없어요.\n점수에 어떤 근거가 얼마나 기여했는지 나눠 보고,\n가장 큰 근거를 챔피언 얼굴로 붙여요.',
        synergy: '시너지',
        counter: '카운터',
      },
      patch: {
        title: '패치가 바뀌면, 그날 바로',
        body: '롤은 2주마다 패치가 나와요.\n새 패치 경기로 모델을 다시 학습하는 시간을 줄여서\n지난 패치 기준으로 추천하는 시간을 크게 줄였어요.',
        before: '이전',
        after: '지금',
        beforeValue: '약 15시간',
        afterValue: '약 2시간 반',
        caption: '새 패치가 추천에 반영되기까지',
      },
      speed: {
        title: '밴픽 시간 안에, 추천은 1초 안에',
        body: '좋은 추천도 늦으면 못 써요.\n인기 챔피언일수록 느리던 추천을 고쳐서\n이제 모든 챔피언이 1초 안에 답해요.',
        before: '고치기 전',
        after: '지금',
        line: '1초',
        beforeCount: '173개 중 169개가 1초 넘게',
        afterCount: '173개 모두 1초 안에',
        caption: '점 하나가 챔피언 하나예요. 높이는 응답 시간의 대략적인 모양이에요.',
      },
    },
    cta: {
      title: '다음 판부터 바로',
      body: '설치하고 롤을 켜면 끝. 따로 설정할 건 없어요.',
    },
  },
  footer: {
    privacy: '개인정보처리방침',
    trademark: 'League of Legends와 Riot Games는 Riot Games, Inc.의 상표입니다.',
  },
  privacy: {
    title: '개인정보처리방침',
    lastUpdatedLabel: '최종 수정일',
    lastUpdated: '2026년 10월 2일',
    intro: '본 방침은 DFGG 웹사이트(https://dfgg.pro)와 DFGG 데스크톱 앱에 모두 적용됩니다.',
    contactLabel: '개인정보 관련 문의',
    sections: [
      {
        title: '1. 수집하지 않는 정보',
        body: [
          'DFGG는 회원가입과 로그인이 없습니다. 이름, 이메일, 결제 정보를 수집하지 않으며 광고 식별자를 사용하지 않습니다.',
        ],
      },
      {
        title: '2. 처리하는 정보',
        body: [
          '아이템 추천을 위해 한 경기에 등장하는 챔피언 10개의 이름과 포지션을 서버로 전송합니다. 이 정보만으로는 개인을 식별할 수 없습니다.',
          '데스크톱 앱은 사용자의 PC에서 실행 중인 League of Legends 클라이언트로부터 로컬 주소(127.0.0.1)를 통해 경기 정보를 읽습니다. 이 과정에서 사용자 본인의 Riot ID와 경기 정보를 확인하며, 아래 3항의 사용 기록에 포함되어 전송됩니다.',
        ],
      },
      {
        title: '3. 자동으로 기록되는 정보',
        body: [
          '서버 운영과 장애 대응을 위해 웹 서버 접속 기록(IP 주소, 접속 시각, 요청 경로)이 남습니다. 이 기록은 통계나 마케팅에 사용하지 않습니다.',
          '또한 서비스 개선을 위해 웹사이트와 데스크톱 앱의 사용 기록을 수집합니다.',
        ],
        list: [
          '웹사이트: 방문한 페이지, 버튼 클릭 등 기능 사용 기록',
          '데스크톱 앱: Riot ID와 이를 기반으로 한 사용자 식별값, 앱 버전, 플레이한 경기 정보(챔피언, 포지션, 게임 모드, 승패, 경기 ID, 아이템 구매 및 추천 이용 횟수)',
          '공통: 접속 IP로 추정한 국가·도시, 브라우저·운영체제 종류',
        ],
        after: [
          '웹사이트는 재방문 여부를 구분하기 위해 쿠키와 브라우저 저장소를 사용합니다. 브라우저 설정에서 쿠키를 차단하거나 삭제할 수 있으며, 차단해도 서비스 이용에는 지장이 없습니다. 수집한 기록은 서비스 개선 외의 목적이나 마케팅·광고에 사용하지 않습니다.',
          '수집한 사용 기록은 수집일로부터 1년간 보관한 뒤 삭제합니다. 삭제를 원하면 Riot ID를 적어 아래 메일로 요청해 주세요.',
        ],
      },
      {
        title: '4. 제3자 서비스',
        body: [
          '데스크톱 앱의 챔피언 및 아이템 이미지는 Riot Games가 운영하는 Data Dragon(ddragon.leagueoflegends.com)에서 불러옵니다.',
          '사용 기록 집계를 위해 PostHog Inc.의 PostHog Cloud(미국)와 Umami Software, Inc.의 Umami Cloud를 사용합니다. 기록은 국외 서버에서 처리되며, 각 서비스의 개인정보처리방침(https://posthog.com/privacy, https://umami.is/privacy)을 따릅니다.',
        ],
      },
    ],
  },
  items: {
    2510: { name: '황혼과 새벽', traits: ['주문검', '공격 속도'] },
    3158: { name: '명석함의 아이오니아 장화', traits: ['스킬 가속'] },
    6675: { name: '나보리 명멸검', traits: ['치명타', '스킬 쿨타임 감소'] },
    3065: { name: '정령의 형상', traits: ['마법 저항력', '회복 및 보호막 강화'] },
    3047: { name: '판금 장화', traits: ['방어력', '기본 공격 피해 감소'] },
    3111: { name: '헤르메스의 발걸음', traits: ['마법 저항력', '강인함'] },
    3075: { name: '가시 갑옷', traits: ['치유 감소', '방어력'] },
    3742: { name: '망자의 갑옷', traits: ['방어력', '이동 속도'] },
    2502: { name: '끝없는 절망', traits: ['방어력', '스킬 가속'] },
    3143: { name: '란두인의 예언', traits: ['방어력', '치명타 피해 감소'] },
    4401: { name: '대자연의 힘', traits: ['마법 저항력', '이동 속도'] },
    6665: { name: '해신 작쇼', traits: ['방어력', '마법 저항력'] },
    2504: { name: '케이닉 루컨', traits: ['마법 저항력', '보호막'] },
    4629: { name: '우주의 추진력', traits: ['주문력', '이동 속도'] },
    // '지금 살 것'에 나오는 하위 아이템
    3057: { name: '광휘의 검', traits: [] },
    1001: { name: '장화', traits: [] },
    1033: { name: '마법무효화의 망토', traits: [] },
    1029: { name: '천 갑옷', traits: [] },
    3067: { name: '점화석', traits: [] },
    3211: { name: '망령의 두건', traits: [] },
    3082: { name: '파수꾼의 갑옷', traits: [] },
    1011: { name: '거인의 허리띠', traits: [] },
    1031: { name: '쇠사슬 조끼', traits: [] },
  } as Record<number, { name: string; traits: string[] }>,
  champions: {
    Jax: '잭스',
    Veigar: '베이가',
    Sivir: '시비르',
    Lulu: '룰루',
    Sylas: '사일러스',
    Yunara: '유나라',
    Ahri: '아리',
    TahmKench: '탐 켄치',
    Seraphine: '세라핀',
  } as Record<string, string>,
};

const EN: typeof KO = {
  meta: META.en,
  header: {
    navLabel: 'Main menu',
    homeLabel: 'home',
    skipToContent: 'Skip to content',
    installGuide: 'Install guide',
    features: 'Features',
    download: 'Download',
    switchLanguage: '한국어',
    switchLanguageLabel: '한국어',
  },
  download: {
    store: 'Get it free on Microsoft Store',
    exe: 'Download installer (.exe)',
    requirement: 'Windows 10·11',
    stats: [
      { value: '1K+', label: 'downloads' },
      { value: '5.0', label: 'user rating' },
      { value: '$0', label: 'free forever' },
    ],
  },
  hero: {
    chatHistory: { name: 'TopBladeGaren', champion: 'Renekton', text: 'adc what u building?' },
    chatTyped: { name: 'ADCMain', champion: 'Jinx', text: 'what should i build?' },
    headline: ['Stop asking.'],
    subtitle: {
      prefix: 'DFGG reads ',
      teamComp: 'both team comps',
      joiner: ' and the ',
      liveState: 'live game',
      suffix: ',',
      secondLinePrefix: 'then recommends your next ',
      coreItem: 'core item',
      secondLineSuffix: '!',
    },
  },
  demo: {
    heading: ['In game,', 'it looks like this!'],
    stepsLabel: 'Demo steps',
    overlayLabel: 'Example of the DFGG overlay shown over the game',
    pauseVideo: 'Pause background video',
    playVideo: 'Play background video',
    stages: [
      {
        label: 'Game start',
        title: 'Right there in your game!',
        body: 'Once the game starts, your top 3 core items and 2 alternatives show up on screen.',
      },
      {
        label: 'Buy an item',
        title: 'Buy an item, get the next core!',
        body: 'Recommendations refresh every time you buy.',
      },
      {
        label: 'Why this item',
        title: 'Why this item? We tell you!',
        body: 'See who it counters\nand which allies it works best with.',
      },
      {
        label: 'Full build',
        title: 'Ready to play?!',
        body: 'Follow the picks one by one until all six slots are filled.',
      },
    ],
  },
  overlay: {
    coreTitle: (core: number) => `Core ${core} picks`,
    synergy: 'Synergy',
    counter: 'Counter',
    allyRelation: 'Ally — good synergy',
    counterRelation: 'Enemy — good counter',
    rank: (n: number) => `Pick #${n}`,
    progress: (total: number, done: number) => `${done} of ${total} items bought`,
    nowBuy: 'Buy now',
    shortage: (gold: string) => `${gold} short`,
    situational: 'Vs. enemy comp',
    expand: 'Expand overlay',
    collapse: 'Collapse overlay',
    inventoryLabel: (count: number) => `Inventory: ${count} slots filled`,
  },
  install: {
    title: 'Seeing a blue warning?\nThat’s okay.',
    highlight: 'It shows up because we’re still getting certified!',
    body: [
      'Windows blocks the .exe once because it doesn’t recognize the publisher.',
      'That’s because we don’t have a code signing certificate yet.',
    ],
    followMobile: 'Click through like the window below to install.',
    followDesktop: 'Click through like the window on the right to install.',
    store: {
      prefix: 'To install without the warning, get the ',
      link: 'Microsoft Store version',
      suffix: '.',
    },
    faqTitle: 'FAQ',
    faq: [
      {
        q: 'I can’t see the overlay',
        a: 'The overlay is hidden when League runs in fullscreen. DFGG switches it to borderless outside of games (and backs up your original setting), but if you changed it back, set Settings → Video → Window Mode to Borderless.',
      },
      {
        q: 'Recommendations don’t show up',
        a: 'They appear once the game starts. They won’t show in the lobby or champ select, and the League client must be running to connect.',
      },
      {
        q: 'My browser blocked the download',
        a: 'Open your browser’s download list and choose Keep. The file comes straight from DFGG’s GitHub releases.',
      },
      {
        q: 'Does it work on Mac?',
        a: 'Only Windows is supported for now.',
      },
    ],
  },
  smartScreen: {
    title: 'Windows protected your PC',
    body: 'Microsoft Defender SmartScreen prevented an unrecognized app from starting. Running this app might put your PC at risk.',
    moreInfo: 'More info',
    app: 'App:',
    publisher: 'Publisher:',
    unknownPublisher: 'Unknown publisher',
    run: 'Run anyway',
    dontRun: 'Don’t run',
    stepMoreInfo: '1. Click “More info”',
    stepRun: '2. Click “Run anyway” to start installing',
    done: 'Installation starts',
    replay: 'Replay',
  },
  features: {
    title: 'Features - DFGG',
    kicker: 'Inside DFGG',
    headline: ['From champ select', 'to the last fight'],
    intro:
      'While you pick, while you play, and after the game.\nHere is what DFGG does, shown with the real app.',
    stepsLabel: 'One game, start to finish',
    steps: [
      {
        key: 'champ-select',
        label: 'Champ select',
        title: 'See what the pros picked first',
        body: 'When champ select starts, DFGG switches to a draft view.\nSee what pros in your role picked at the latest tournament,\nand who counters the champions the enemy locks in.',
        points: [
          { name: "Today's pick", text: 'Recent pro picks\nwith their runes and items' },
          {
            name: 'Counter picks',
            text: 'Your likely laner and who beats them,\nupdated on every enemy pick',
          },
          {
            name: 'Auto runes',
            text: 'Pick a champion and the runes\ngo straight into your client',
          },
        ],
        imageAlt: "DFGG champ select view with today's pick and counter picks",
        subImageAlt: 'DFGG rune recommendation',
      },
      {
        key: 'in-game',
        label: 'In game',
        title: 'On your screen, down to what to buy now',
        body: 'Once the game starts, a small panel shows up on the left.\nIt reads both team comps and your items, picks your next core,\nand updates every time you buy.',
        points: [
          { name: 'Top 3 cores', text: 'With the allies they fit\nand the enemies they counter' },
          { name: 'Buy now', text: 'The components you can afford\nwith the gold you have' },
          {
            name: 'Situational',
            text: 'Up to two items the enemy comp calls for,\nlike anti-heal',
          },
        ],
        imageAlt: 'DFGG item overlay on the left side of the game screen',
      },
      {
        key: 'post-game',
        label: 'After the game',
        title: 'See where you ranked',
        body: 'When the game ends, DF Score rates all ten players\non the same scale.\nNot just kills, but damage, vision and objectives too.\nFirst place gets MVP.',
        points: [{ name: 'DF Score', text: 'Your rank and score among all ten players' }],
        imageAlt: 'DF Score MVP card for the game that just ended',
        subImageAlt: 'DF Score ranking of all ten players',
      },
      {
        key: 'highlight',
        label: 'Highlights',
        title: 'Your best plays, saved for you',
        body: 'No record button to press.\nWhen you get a kill or take dragon or baron,\nDFGG clips it from the moment the fight started.',
        points: [
          {
            name: 'Auto record',
            text: 'Records when the game starts\nand clips it the moment it ends',
          },
          { name: 'Keep watching', text: 'When one clip ends,\nthe next play starts' },
          { name: 'From history', text: 'Open any game’s highlights\nfrom its match card' },
        ],
        imageAlt: 'Highlight clips playing for the game that just ended',
        subImageAlt: 'Highlight clips opened from a match card',
      },
      {
        key: 'history',
        label: 'Match history',
        title: 'Your history at a glance',
        body: 'DFGG sits next to the League client and shows your recent games.\nOpen one to compare damage, items and DF rank\nacross all ten players.',
        points: [
          { name: 'Recent summary', text: 'Win rate, KDA, most played champions, best game' },
          { name: 'Head to head', text: 'Lane opponents side by side,\ndamage and items' },
        ],
        imageAlt: 'DFGG home screen with recent match summary',
        subImageAlt: 'An opened match comparing damage across ten players',
      },
    ],
    tech: {
      kicker: 'Why you can trust the picks',
      title: ['Not a hunch.', 'Real games.'],
      intro:
        'We did not want to stop at “this champion usually builds that”.\nSo we started by collecting what players actually bought in Korean ranked games.',
      data: {
        title: 'Korean ranked games, collected nonstop',
        body: 'We keep collecting Korean solo queue games on our own.\nA game that shows up in several players’ histories\nis counted once.',
        stats: [
          { value: '~470K', unit: 'games', label: 'solo queue games collected' },
          { value: '~26M', unit: 'rows', label: 'item purchases inside them' },
        ],
        note: 'Numbers are rounded.',
      },
      model: {
        title: 'A recommendation AI built with machine learning',
        body: 'We trained a machine learning model\non item purchases from real Korean ranked games.\nIt learned to rank the item real players bought next\nin that situation at the top.',
        sourcesLabel: 'Four places candidates come from',
        sources: [
          'Items that continue your build',
          'Items this champion likes',
          'Items that fit your allies',
          'Items that counter the enemy',
        ],
        rankedLabel: 'Ranked by the AI model',
        note: 'Off-meta builds like AD LeBlanc stay in.\nOnly items the game will not let you buy are removed.',
      },
      reason: {
        title: 'Every pick comes with a reason',
        body: 'A score alone does not tell you why.\nWe break the score down by how much each reason contributed,\nand show the biggest ones as champion faces.',
        synergy: 'Synergy',
        counter: 'Counter',
      },
      patch: {
        title: 'New patch, same day',
        body: 'League patches every two weeks.\nWe cut the time it takes to retrain on new patch games,\nso you spend far less time on last patch’s picks.',
        before: 'Before',
        after: 'Now',
        beforeValue: '~15 hours',
        afterValue: '~2.5 hours',
        caption: 'Until a new patch shows up in recommendations',
      },
      speed: {
        title: 'Picks in under a second',
        body: 'A good pick is useless if it comes late.\nPopular champions used to be the slowest.\nNow every champion answers in under a second.',
        before: 'Before',
        after: 'Now',
        line: '1s',
        beforeCount: '169 of 173 over a second',
        afterCount: 'All 173 under a second',
        caption: 'Each dot is a champion. Height shows the rough shape of response times.',
      },
    },
    cta: {
      title: 'Ready for your next game',
      body: 'Install it and launch League. Nothing to set up.',
    },
  },
  footer: {
    privacy: 'Privacy Policy',
    trademark: 'League of Legends and Riot Games are trademarks of Riot Games, Inc.',
  },
  privacy: {
    title: 'Privacy Policy',
    lastUpdatedLabel: 'Last updated',
    lastUpdated: 'October 2, 2026',
    intro:
      'This policy applies to both the DFGG website (https://dfgg.pro) and the DFGG desktop app.',
    contactLabel: 'Privacy inquiries',
    sections: [
      {
        title: '1. Information we do not collect',
        body: [
          'DFGG has no sign-up or login. We do not collect your name, email, or payment information, and we do not use advertising identifiers.',
        ],
      },
      {
        title: '2. Information we process',
        body: [
          'To recommend items, we send the names and positions of the 10 champions in a match to our server. This alone cannot identify you.',
          'The desktop app reads match information from the League of Legends client running on your PC through a local address (127.0.0.1). In doing so it reads your Riot ID and match information, which are sent as part of the usage records described in section 3.',
        ],
      },
      {
        title: '3. Automatically recorded information',
        body: [
          'For server operation and troubleshooting, our web server keeps access logs (IP address, access time, request path). These logs are not used for statistics or marketing.',
          'To improve the service, we also collect usage records from the website and the desktop app.',
        ],
        list: [
          'Website: pages visited and feature usage such as button clicks',
          'Desktop app: your Riot ID and an identifier derived from it, app version, and information about matches you play (champion, position, game mode, result, match ID, item purchases and recommendation usage counts)',
          'Both: country and city estimated from your IP address, browser and operating system type',
        ],
        after: [
          'The website uses cookies and browser storage to recognize returning visitors. You can block or delete cookies in your browser settings; this does not affect your use of the service. Usage records are used only to improve the service and never for marketing or advertising.',
          'Usage records are kept for one year from collection and then deleted. To request deletion, email us at the address below with your Riot ID.',
        ],
      },
      {
        title: '4. Third-party services',
        body: [
          'Champion and item images in the desktop app are loaded from Data Dragon (ddragon.leagueoflegends.com), operated by Riot Games.',
          'We use PostHog Cloud (United States) by PostHog Inc. and Umami Cloud by Umami Software, Inc. to aggregate usage records. Records are processed on servers outside Korea under each service’s privacy policy (https://posthog.com/privacy, https://umami.is/privacy).',
        ],
      },
    ],
  },
  items: {
    2510: { name: 'Dusk and Dawn', traits: ['Spellblade', 'Attack speed'] },
    3158: { name: 'Ionian Boots of Lucidity', traits: ['Ability haste'] },
    6675: { name: 'Navori Flickerblade', traits: ['Crit', 'Ability haste'] },
    3065: { name: 'Spirit Visage', traits: ['Heal & shield boost'] },
    3047: { name: 'Plated Steelcaps', traits: ['Armor', 'Less auto damage'] },
    3111: { name: 'Mercury’s Treads', traits: ['Magic resist', 'Tenacity'] },
    3075: { name: 'Thornmail', traits: ['Grievous wounds', 'Armor'] },
    3742: { name: 'Dead Man’s Plate', traits: ['Armor', 'Move speed'] },
    2502: { name: 'Unending Despair', traits: ['Armor', 'Ability haste'] },
    3143: { name: 'Randuin’s Omen', traits: ['Armor', 'Less crit damage'] },
    4401: { name: 'Force of Nature', traits: ['Magic resist', 'Move speed'] },
    6665: { name: 'Jak’Sho, The Protean', traits: ['Armor', 'Magic resist'] },
    2504: { name: 'Kaenic Rookern', traits: ['Magic resist', 'Shield'] },
    4629: { name: 'Cosmic Drive', traits: ['Ability power', 'Move speed'] },
    3057: { name: 'Sheen', traits: [] },
    1001: { name: 'Boots', traits: [] },
    1033: { name: 'Null-Magic Mantle', traits: [] },
    1029: { name: 'Cloth Armor', traits: [] },
    3067: { name: 'Kindlegem', traits: [] },
    3211: { name: 'Spectre’s Cowl', traits: [] },
    3082: { name: 'Warden’s Mail', traits: [] },
    1011: { name: 'Giant’s Belt', traits: [] },
    1031: { name: 'Chain Vest', traits: [] },
  },
  champions: {
    Jax: 'Jax',
    Veigar: 'Veigar',
    Sivir: 'Sivir',
    Lulu: 'Lulu',
    Sylas: 'Sylas',
    Yunara: 'Yunara',
    Ahri: 'Ahri',
    TahmKench: 'Tahm Kench',
    Seraphine: 'Seraphine',
  },
};

const LOCALE_PREFIX = /^\/(ko|en)(?=\/|$)/;

export function localeFromPath(pathname: string): Locale | null {
  const match = pathname.match(LOCALE_PREFIX);
  return match ? (match[1] as Locale) : null;
}

export function stripLocale(pathname: string): string {
  return pathname.replace(LOCALE_PREFIX, '') || '/';
}

export const LOCALE: Locale =
  localeFromPath(window.location.pathname) ?? pickLocale(navigator.languages);

// 언어별 첫 화면은 빌드 때 /ko/index.html, /en/index.html 로 따로 만든다.
// 서버가 /ko 를 /ko/ 로 넘기므로 처음부터 끝에 / 를 붙여 한 번 더 오가지 않게 한다.
export const localePath = (locale: Locale, path = '/'): string =>
  `/${locale}${path === '/' ? '/' : path}`;

export function strings(locale: Locale = LOCALE): typeof KO {
  if (locale === 'ko') return KO;
  return EN;
}
