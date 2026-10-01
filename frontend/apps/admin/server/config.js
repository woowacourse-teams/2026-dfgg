/**
 * 대시보드가 읽는 이벤트 이름 모음.
 * 우마미에 실제로 쌓인 이름 기준이다 — 앱에서 이벤트 이름을 바꾸면 여기도 같이 바꾼다.
 * 한 단계에 이름을 여러 개 넣으면 우마미가 "그중 하나라도 보낸 방문자"를 중복 없이 센다.
 */

const DESKTOP_EVENT_PREFIX = 'desktop-';

const FUNNEL = [
  { key: 'web-visit', label: '웹 방문', source: 'analytics-web' },
  {
    key: 'app-cta',
    label: '앱 받기 클릭',
    source: 'analytics-event',
    events: [
      'download-click',
      'store-click-top',
      'store-click-bottom',
      'desktop-app-home',
      'desktop-app-champion-topbar',
      'desktop-app-champion-select',
      'header-install-guide-click',
    ],
  },
  { key: 'store-acquisition', label: '스토어 설치', source: 'store' },
  {
    key: 'app-launch',
    label: '앱 실행',
    source: 'analytics-event',
    events: ['desktop-app-launch', 'desktop-launch'],
  },
  {
    key: 'lcu-connect',
    label: '롤 클라이언트 연결',
    source: 'analytics-event',
    events: ['desktop-lcu-connected', 'desktop-connect'],
  },
  {
    key: 'recommend',
    label: '인게임 추천 성공',
    source: 'analytics-event',
    events: ['desktop-recommend-success', 'desktop-recommend-v3-success'],
  },
];

// 일별 추이에 "앱 실행"으로 그리는 이벤트
const LAUNCH_EVENTS = ['desktop-app-launch', 'desktop-launch'];

// recommend(-v3)-success / -fail / -error 형태를 성공·실패로 묶는다.
const RECOMMEND_PATTERN = /recommend(?:-v\d+)?-(success|fail|error)$/;

module.exports = { DESKTOP_EVENT_PREFIX, FUNNEL, LAUNCH_EVENTS, RECOMMEND_PATTERN };
