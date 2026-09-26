// 오버레이 접힐 때 크기
export const COLLAPSED_WIDTH = 40;
export const COLLAPSED_HEIGHT = 40;

// 오버레이 크기
// 높이는 아이템 3개가 보이도록 계산했다.
//   타이틀바 40 + content 상하 패딩 16 + 아이템 58 × 3 + 아이템 사이 gap 6 × 2 = 242
// (아이템 58 = 상하 패딩 12 + 본문 46[설명 15 + gap 5 + 챔피언 26])
// 넓이는 "회복 및 보호막 강화" 정도의 설명이 한 줄에 들어가는 최소치다.
export const EXPANDED_WIDTH = 260;
export const EXPANDED_HEIGHT = 242;
