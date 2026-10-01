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
  meta: {
    title: 'DFGG - 조합 맞춤 아이템 추천 데스크톱 앱',
    description:
      '게임이 시작되면 양 팀 챔피언과 산 아이템을 보고 다음 코어 아이템을 게임 화면에 띄워주는 리그 오브 레전드 데스크톱 앱이에요.',
  },
  header: {
    navLabel: '주요 메뉴',
    homeLabel: 'DFGG 홈',
    installGuide: '설치 안내',
    switchLanguage: 'EN',
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
    expand: '오버레이 켜기',
    collapse: '오버레이 끄기',
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
  meta: {
    title: 'DFGG - Build recommendations for your team comp',
    description:
      'A League of Legends desktop app that reads both team comps and your items once the game starts, then shows your next core item right on the game screen.',
  },
  header: {
    navLabel: 'Main menu',
    homeLabel: 'DFGG home',
    installGuide: 'Install guide',
    switchLanguage: '한국어',
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
    expand: 'Show overlay',
    collapse: 'Hide overlay',
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

export const localePath = (locale: Locale, path = '/'): string =>
  `/${locale}${path === '/' ? '' : path}`;

export function strings(locale: Locale = LOCALE): typeof KO {
  if (locale === 'ko') return KO;
  return EN;
}
