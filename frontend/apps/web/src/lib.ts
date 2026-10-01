export const MS_STORE_URL = 'https://apps.microsoft.com/detail/9nxl98m7xc82?hl=ko-KR&gl=KR';

export const DOWNLOAD_URL =
  'https://github.com/woowacourse-teams/2026-dfgg/releases/latest/download/dfgg-setup.exe';

const S3baseUrl = 'https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/dfgg/images';
const DDRAGON_VERSION = '16.18';

export const championIcon = (id: string) => `${S3baseUrl}/${DDRAGON_VERSION}/champions/${id}.png`;
export const itemIcon = (id: number) => `${S3baseUrl}/${DDRAGON_VERSION}/items/${id}.png`;

type TrackData = Record<string, string | number>;

const trackedOnce = new Set<string>();

export const track = (event: string, data?: TrackData) => window.umami?.track(event, data);

export const trackOnce = (event: string, data?: TrackData) => {
  const key = `${event}:${JSON.stringify(data ?? {})}`;
  if (trackedOnce.has(key)) return;
  trackedOnce.add(key);
  track(event, data);
};

export const getOs = () => {
  const ua = navigator.userAgent;
  if (/Android|iPhone|iPad|iPod/i.test(ua)) return 'mobile';
  if (/Windows/i.test(ua)) return 'windows';
  if (/Mac/i.test(ua)) return 'mac';
  return 'other';
};
