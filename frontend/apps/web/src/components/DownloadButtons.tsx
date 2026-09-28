// 다운로드 버튼 파일
import { DOWNLOAD_URL, getOs, MS_STORE_URL } from '../lib';

export default function DownloadButtons() {
  const os = getOs();

  return (
    <div className='flex flex-col gap-3 sm:flex-row'>
      <a
        data-umami-event='store-click-top'
        data-umami-event-os={os}
        href={MS_STORE_URL}
        target='_blank'
        rel='noreferrer'
        className='rounded-md bg-accent-strong px-6 py-3.5 text-center font-bold text-white shadow-[0_8px_30px_-8px_rgb(230_57_80/0.7)] transition-colors hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent active:bg-accent-deep'
      >
        Microsoft Store에서 무료로 받기
      </a>
      <a
        data-umami-event='download-click'
        data-umami-event-os={os}
        href={DOWNLOAD_URL}
        className='rounded-md px-6 py-3.5 text-center font-medium text-ink-2 ring-1 ring-line transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent'
      >
        설치 파일(.exe) 받기
      </a>
    </div>
  );
}
