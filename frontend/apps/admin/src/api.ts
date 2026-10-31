// 로컬 관리자 API 주소. 실제 호출은 server/index.js 가 받는다.
export const summaryUrl = (days: number) => `/admin-api/summary?days=${days}`;
export const playersUrl = (days: number) => `/admin-api/players?days=${days}`;
