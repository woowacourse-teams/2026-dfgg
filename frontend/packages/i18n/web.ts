/** 웹사이트 문구. 페이지별로 묶어 둔다. */

export const NAV_TEXT = {
  ko: {
    feedback: '피드백',
    desktopApp: '데스크톱 앱',
    backHome: '← 처음으로',
    langLabel: '언어',
  },
  en: {
    feedback: 'Feedback',
    desktopApp: 'Desktop app',
    backHome: '← Home',
    langLabel: 'Language',
  },
};

export const HOME_TEXT = {
  ko: {
    headlineTop: '나의 조합, 상대 조합에 맞는',
    headlineAccent: '6가지',
    headlineRest: '아이템 추천',
    cta: '추천 받아보기',
    tryIt: '체험해보기',
    features: [
      {
        eyebrow: '데스크톱 앱',
        title: '조합 자동 인식',
        body: [
          '밴픽이 끝나면 롤 클라이언트에서 양 팀 조합을 그대로 읽어옵니다.',
          '열 명을 하나씩 입력할 필요가 없습니다.',
        ],
        alt: '데스크톱 앱이 양 팀 조합을 읽어와 추천 아이템을 보여주는 화면',
      },
      {
        eyebrow: '인게임 오버레이',
        title: '게임 위에 바로',
        body: [
          '추천 아이템 6개를 순서대로 게임 화면 위에 띄워줍니다.',
          '알트탭 없이 그 자리에서 확인하세요.',
        ],
        alt: '게임 화면 위에 추천 아이템 6개가 떠 있는 오버레이',
      },
    ],
  },
  en: {
    headlineTop: 'Built for your team and theirs',
    headlineAccent: 'Six',
    headlineRest: 'item recommendations',
    cta: 'Get recommendations',
    tryIt: 'Try it out',
    features: [
      {
        eyebrow: 'Desktop app',
        title: 'Reads your lineup automatically',
        body: [
          'Once champion select ends, both teams are read straight from the League client.',
          'No need to type in all ten champions.',
        ],
        alt: 'The desktop app reading both lineups and showing recommended items',
      },
      {
        eyebrow: 'In-game overlay',
        title: 'Right on top of the game',
        body: [
          'Six recommended items appear in order on top of your game.',
          'Check them without alt-tabbing.',
        ],
        alt: 'An overlay showing six recommended items on top of the game',
      },
    ],
  },
};

export const CHAMPION_SELECT_TEXT = {
  ko: {
    titleAccent: '조합',
    titleRest: '기반 아이템 추천',
    filled: ' / 10 입력',
    modeTablist: '추천 방식',
    mode1: '1번 · 빌드 추천',
    mode2: '2번 · 하나씩 골라 추천',
    guideBefore: '왼쪽 칸에 ',
    guideAllies: '아군',
    guideMiddle: ' 5명, 오른쪽 칸에 ',
    guideEnemies: '상대',
    guideAfter: ' 5명을 포지션에 맞춰 입력해 주세요.',
    randomPick: '랜덤 챔피언 뽑기',
    listFailed: '챔피언 목록을 불러오지 못했어요. 이름을 직접 입력해 주세요.',
    positionHintBefore: '가운데 포지션 버튼을 눌러 내 포지션을 정하세요. 현재 ',
    positionHintAfter: ' 기준으로 추천해요.',
    allies: '아군',
    enemies: '상대',
    myChampion: '내 챔피언',
    me: '나',
    setMyPosition: (position: string) => `${position}를 내 포지션으로`,
    recommend: (label: string) => `${label} 추천`,
    analyzing: '분석 중...',
    submit: '아이템 추천받기',
    incomplete: '목록에서 챔피언 10명을 모두 선택해 주세요.',
    requestFailed: '추천을 불러오지 못했어요. 다시 시도해 주세요.',
    noBuild: '이 조합에 맞는 추천 빌드를 아직 찾지 못했어요.',
    nextCore: '다음 코어템 후보',
    restart: '처음부터 다시 고르기',
    chosenSoFar: '지금까지 고른 아이템',
    noData: '이 조합은 아직 데이터가 부족해요.',
    pickHint:
      '이 중 하나를 클릭해서 다음 코어템으로 고르세요. 순서가 아니라 후보예요. 아이템에 마우스를 올리면 이름이 보여요.',
    ctaTitle: '추천 템트리 괜찮으셨나요?',
    ctaBody: [
      '데스크톱 앱은 롤 클라이언트에서 조합을 자동으로 읽어옵니다.',
      '다음 판부터는 입력하지 않아도 됩니다.',
    ],
    comboboxPlaceholder: '챔피언 이름',
  },
  en: {
    titleAccent: 'Lineup',
    titleRest: '-based item recommendations',
    filled: ' / 10 filled',
    modeTablist: 'Recommendation mode',
    mode1: 'Mode 1 · Full build',
    mode2: 'Mode 2 · Pick one at a time',
    guideBefore: 'Fill the left column with your ',
    guideAllies: 'allies',
    guideMiddle: ' (5) and the right column with the ',
    guideEnemies: 'enemy team',
    guideAfter: ' (5), matched to their positions.',
    randomPick: 'Random champions',
    listFailed: "Couldn't load the champion list. Please type the names instead.",
    positionHintBefore: 'Use the middle buttons to set your position. Currently recommending for ',
    positionHintAfter: '.',
    allies: 'Allies',
    enemies: 'Enemies',
    myChampion: 'My champion',
    me: 'Me',
    setMyPosition: (position: string) => `Set ${position} as my position`,
    recommend: (label: string) => `${label} recommendation`,
    analyzing: 'Analyzing...',
    submit: 'Get item recommendations',
    incomplete: 'Please choose all 10 champions from the list.',
    requestFailed: "Couldn't load recommendations. Please try again.",
    noBuild: "We haven't found a build for this lineup yet.",
    nextCore: 'Next core item candidates',
    restart: 'Start over',
    chosenSoFar: 'Picked so far',
    noData: "There isn't enough data for this lineup yet.",
    pickHint:
      'Click one to choose it as your next core item. These are candidates, not an order. Hover an item to see its name.',
    ctaTitle: 'How were the recommendations?',
    ctaBody: [
      'The desktop app reads your lineup straight from the League client.',
      "From the next game on, you won't have to type anything.",
    ],
    comboboxPlaceholder: 'Champion name',
  },
};

export const FEEDBACK_TEXT = {
  ko: {
    title: '피드백',
    intro: '추천이 어땠는지, 무엇이 불편했는지 편하게 적어주세요. 한 줄이어도 괜찮습니다.',
    label: '피드백 내용',
    placeholder: '예) 상대에 탱커가 많은데 방어구 관통 아이템이 안 나와요.',
    submit: '보내기',
    failed: '에러가 발생했습니다. 다시 시도해 주세요.',
  },
  en: {
    title: 'Feedback',
    intro: 'Tell us how the recommendations felt and what got in your way. A single line is fine.',
    label: 'Your feedback',
    placeholder: 'e.g. The enemy team is full of tanks but no armor penetration items showed up.',
    submit: 'Send',
    failed: 'Something went wrong. Please try again.',
  },
};

export const DESKTOP_PAGE_TEXT = {
  ko: {
    title: '데스크톱 앱',
    intro: [
      '열 명을 직접 입력하지 않아도 됩니다.',
      '롤 클라이언트에서 조합을 자동으로 읽어 게임 위에 추천 아이템을 띄워줍니다.',
    ],
    downloadExe: 'exe 파일로 받기',
    downloadStore: 'Microsoft Store에서 받기',
    notes: [
      '내려받은 파일을 두 번 누르면 설치되고 바로 실행됩니다. 이후 새 버전이 나오면 앱이 알아서 받아두고, 다시 시작할 때 적용됩니다.',
      '롤 클라이언트가 실행 중이어야 밴픽·게임 조합을 읽어옵니다.',
    ],
    fullscreenNote: [
      '롤이 ',
      '전체 화면',
      '이면 오버레이가 게임에 가려집니다. 롤 설정 → 그래픽 → 창 모드를 ',
      '테두리 없음',
      '이나 ',
      '창 모드',
      '로 두세요.',
    ],
    howItWorks: '이렇게 동작합니다',
    beforeDownload: '다운로드 하기 전에 확인해주세요',
    betaTitle: '아직 베타 버전이고, Riot Games의 승인을 받은 앱이 아닙니다',
    betaBody: [
      'DFGG는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다.',
      '저희가 직접 만들어 시험 삼아 공개한 베타 앱이라 예상치 못한 문제가 생길 수 있습니다.',
      'Riot Games의 정책에 어긋난다는 판단이 서면 저희가 배포를 중단하겠습니다.',
    ],
    blockedTitle: ['다운로드가 막히면 ', '계속', ' 또는 ', '유지', '를 눌러주세요'],
    blockedBody: [
      '코드 서명 인증서를 아직 준비하는 중이라, 브라우저와 Windows가 DFGG를 처음 보는 파일로 취급해 경고를 띄웁니다. 바이러스가 발견된 게 아니라 ',
      '누가 만들었는지 확인할 수 없다',
      '는 뜻입니다.',
    ],
    blockedSteps: [
      ['· 다운로드가 멈추면 브라우저 다운로드 목록에서 ', '계속', ' 또는 ', '유지'],
      ['· 처음 실행할 때 파란 창이 뜨면 ', '추가 정보 → 실행'],
    ],
    blockedNote:
      '파일은 팀 GitHub 릴리스에서 그대로 내려받습니다. 인증서가 준비되면 이 단계는 사라집니다.',
    storeTitle: 'Microsoft 스토어에서 받을 수 있습니다',
    storeBody:
      'Microsoft Store 버전은 검수를 거쳐 올라가기 때문에 위와 같은 경고 없이 설치되고, 새 버전이 나오면 자동으로 갱신됩니다. 마이크로소프트 웹에서보다 스토어에서 다운받는 것을 추천합니다.',
    storeLink: 'Microsoft Store에서 받기 →',
  },
  en: {
    title: 'Desktop app',
    intro: [
      "You don't have to type in all ten champions.",
      'It reads your lineup from the League client and shows recommended items on top of the game.',
    ],
    downloadExe: 'Download the .exe',
    downloadStore: 'Get it on Microsoft Store',
    notes: [
      'Double-click the downloaded file to install and launch it. New versions download in the background and apply the next time you restart.',
      'The League client has to be running for your draft and game lineup to be read.',
    ],
    fullscreenNote: [
      'If League runs in ',
      'Fullscreen',
      ', the overlay is hidden behind the game. Set League Settings → Graphics → Window Mode to ',
      'Borderless',
      ' or ',
      'Windowed',
      '.',
    ],
    howItWorks: 'How it works',
    beforeDownload: 'Before you download',
    betaTitle: "This is still a beta, and it isn't approved by Riot Games",
    betaBody: [
      'DFGG is not affiliated with or endorsed by Riot Games.',
      'We built it ourselves and released it as a beta, so unexpected problems can happen.',
      "If we conclude it conflicts with Riot Games' policies, we will stop distributing it.",
    ],
    blockedTitle: ['If the download is blocked, choose ', 'Continue', ' or ', 'Keep'],
    blockedBody: [
      "We're still getting a code signing certificate, so browsers and Windows treat DFGG as a file they've never seen and show a warning. It doesn't mean a virus was found — it means ",
      "they can't verify who made it",
      '.',
    ],
    blockedSteps: [
      [
        '· If the download stalls, open your browser downloads and choose ',
        'Continue',
        ' or ',
        'Keep',
      ],
      ['· If a blue window appears on first launch, choose ', 'More info → Run'],
    ],
    blockedNote:
      "The file comes straight from our team's GitHub releases. This step goes away once the certificate is in place.",
    storeTitle: 'You can get it on the Microsoft Store',
    storeBody:
      'The Microsoft Store build is reviewed before publishing, so it installs without those warnings and updates itself automatically. We recommend the Store over the direct download.',
    storeLink: 'Get it on Microsoft Store →',
  },
};

export const SLIDES_TEXT = {
  ko: {
    region: '데스크톱 앱 소개',
    roledescription: '슬라이드',
    prev: '이전 슬라이드',
    next: '다음 슬라이드',
    dot: (index: number) => `${index}번째 슬라이드`,
    alts: [
      '밴픽 연동 — 롤 클라이언트 옆에 붙어, 밴픽 화면의 조합을 자동으로 읽어옵니다',
      '조합 분석 — 우리 팀과 상대 팀을 함께 보고, 지금 이 판에 맞는 템을 추천합니다',
      '인게임 오버레이 — 게임이 시작되면 추천 템트리가 화면 위 오버레이로 따라붙습니다',
      '단축키·크기 — Alt+D 한 번으로 켜고 끄고, 오버레이 크기는 네 단계로 조절합니다',
      '자동 연결 — 클라이언트에 붙는 작은 창 하나, 설치하면 바로 연결됩니다',
    ],
  },
  en: {
    region: 'Desktop app tour',
    roledescription: 'slide',
    prev: 'Previous slide',
    next: 'Next slide',
    dot: (index: number) => `Slide ${index}`,
    alts: [
      'Draft integration — sits next to the League client and reads the draft lineup automatically',
      'Lineup analysis — looks at both teams and recommends items for this specific game',
      'In-game overlay — once the game starts, the build follows you as an on-screen overlay',
      'Shortcut and size — toggle with Alt+D and pick from four overlay sizes',
      'Automatic connection — one small window next to the client, connected as soon as you install',
    ],
  },
};

export const PRIVACY_TEXT = {
  ko: {
    back: '← DFGG',
    title: '개인정보처리방침',
    lastUpdatedLabel: '최종 수정일',
    lastUpdated: '2026년 8월 25일',
    scope: '본 방침은 DFGG 웹사이트(https://dfgg.pro)와 DFGG 데스크톱 앱에 모두 적용됩니다.',
    sections: [
      {
        title: '1. 수집하지 않는 정보',
        body: [
          'DFGG는 회원가입과 로그인이 없습니다. 이름, 이메일, 소환사명, Riot ID, PUUID, 결제 정보를 수집하거나 저장하지 않습니다. 광고 식별자나 추적 쿠키도 사용하지 않습니다.',
        ],
      },
      {
        title: '2. 처리하는 정보',
        body: [
          '아이템 추천을 위해 한 경기에 등장하는 챔피언 10개의 이름과 포지션을 서버로 전송합니다. 이 정보만으로는 개인을 식별할 수 없습니다.',
          '데스크톱 앱은 사용자의 PC에서 실행 중인 League of Legends 클라이언트로부터 로컬 주소(127.0.0.1)를 통해 경기 정보를 읽습니다. 이 과정에서 조회되는 소환사명은 10명 중 사용자 본인을 구분하기 위해 PC 안에서만 사용되며 외부로 전송되지 않습니다.',
        ],
      },
      {
        title: '3. 자동으로 기록되는 정보',
        body: [
          '서버 운영과 장애 대응을 위해 웹 서버 접속 기록(IP 주소, 접속 시각, 요청 경로)이 남습니다. 이 기록은 통계나 마케팅에 사용하지 않습니다.',
          '또한 서비스 개선을 위해 웹사이트와 데스크톱 앱에서 방문한 페이지와 기능 사용 기록(예: 버튼 클릭, 추천 요청 성공 여부)을 익명으로 집계합니다. 이 집계에는 쿠키를 사용하지 않으며, 개인을 식별할 수 있는 정보나 경기 내용은 수집하지 않습니다. 마케팅이나 광고에는 사용하지 않습니다.',
        ],
      },
      {
        title: '4. 제3자 서비스',
        body: [
          '챔피언 및 아이템 이미지는 Riot Games가 운영하는 Data Dragon(ddragon.leagueoflegends.com) 에서 직접 불러옵니다.',
          '사용 기록 집계를 위해 Umami Software, Inc. 가 제공하는 Umami Cloud 를 사용합니다. 집계된 기록은 국외 서버에서 처리되며, 해당 처리는 Umami 의 개인정보처리방침 (https://umami.is/privacy)을 따릅니다.',
        ],
      },
    ],
    contactTitle: '5. 문의',
    contactLabel: '개인정보 관련 문의: ',
    noticeTitle: '6. 고지',
    notice: 'DFGG는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다.',
  },
  en: {
    back: '← DFGG',
    title: 'Privacy policy',
    lastUpdatedLabel: 'Last updated',
    lastUpdated: 'August 25, 2026',
    scope:
      'This policy applies to both the DFGG website (https://dfgg.pro) and the DFGG desktop app.',
    sections: [
      {
        title: '1. What we do not collect',
        body: [
          'DFGG has no sign-up and no login. We do not collect or store names, email addresses, summoner names, Riot IDs, PUUIDs, or payment details. We use no advertising identifiers and no tracking cookies.',
        ],
      },
      {
        title: '2. What we process',
        body: [
          'To recommend items, we send the names and positions of the ten champions in a match to our server. That information alone cannot identify an individual.',
          'The desktop app reads match information from the League of Legends client running on your PC over a local address (127.0.0.1). Any summoner name read during this process is used only on your PC, to tell which of the ten players is you, and is never sent anywhere else.',
        ],
      },
      {
        title: '3. What is logged automatically',
        body: [
          'For server operation and incident response, web server access logs (IP address, access time, request path) are recorded. These logs are not used for statistics or marketing.',
          'To improve the service, we also collect anonymous, aggregated usage data from the website and desktop app (for example: button clicks and whether a recommendation request succeeded). This aggregation uses no cookies and collects no personally identifiable information and no match content. It is not used for marketing or advertising.',
        ],
      },
      {
        title: '4. Third-party services',
        body: [
          'Champion and item images are loaded directly from Data Dragon (ddragon.leagueoflegends.com), operated by Riot Games.',
          'We use Umami Cloud, provided by Umami Software, Inc., to aggregate usage data. The aggregated records are processed on servers outside Korea, and that processing follows Umami’s privacy policy (https://umami.is/privacy).',
        ],
      },
    ],
    contactTitle: '5. Contact',
    contactLabel: 'Privacy enquiries: ',
    noticeTitle: '6. Notice',
    notice: 'DFGG is not affiliated with or endorsed by Riot Games.',
  },
};
