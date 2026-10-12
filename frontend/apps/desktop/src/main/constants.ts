// 오버레이 접힐 때 크기
export const COLLAPSED_WIDTH = 40;
export const COLLAPSED_HEIGHT = 40;

// 오버레이 크기
// 넓이는 "회복 및 보호막 강화" 정도의 설명이 한 줄에 들어가는 최소치다.
export const EXPANDED_WIDTH = 260;
// 높이는 화면이 내용에 맞춰 알려 준다(window:set-overlay-height). 이 값은 그 전에 잠깐 쓰는 처음 높이다.
export const EXPANDED_HEIGHT = 242;
// 화면이 알려 주는 높이를 이 범위로 묶는다. 추천이 아주 많아도 게임 화면을 다 가리지 않게 한다.
export const MIN_EXPANDED_HEIGHT = 96;
export const MAX_EXPANDED_HEIGHT = 480;
