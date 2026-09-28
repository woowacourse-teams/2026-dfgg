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
