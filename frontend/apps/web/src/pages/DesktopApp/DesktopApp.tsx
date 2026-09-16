import { useDict, useLang } from '../../../../../packages/i18n/useLang';
import { DESKTOP_PAGE_TEXT } from '../../../../../packages/i18n/web';
import StoreSlides from '../../components/StoreSlides';

/** 스토어 페이지 언어도 화면 언어에 맞춘다. 한국어가 아니면 영어(미국) 스토어로 보낸다. */
const MS_STORE_URL: Record<string, string> = {
  ko: 'https://apps.microsoft.com/detail/9nxl98m7xc82?hl=ko-KR&gl=KR',
  en: 'https://apps.microsoft.com/detail/9nxl98m7xc82?hl=en-US&gl=US',
};

/**
 * 최신 릴리스를 항상 가리킨다. 파일 이름에서 버전을 뺐기 때문에(artifactName)
 * 새 버전을 올려도 이 주소는 그대로다 — 릴리스마다 웹을 다시 배포할 필요가 없다.
 */
const DOWNLOAD_URL =
  'https://github.com/woowacourse-teams/2026-dfgg/releases/latest/download/dfgg-setup.exe';

const BUTTON =
  'chamfer-sm bg-accent-strong px-5 py-3 font-display text-sm font-bold tracking-[0.12em] ' +
  'text-white uppercase transition-colors hover:bg-accent';

/** 제목 왼쪽의 붉은 막대. 세 항목이 같은 모양이라 한 번만 적는다. */
function Marker() {
  return <span aria-hidden='true' className='mt-1.5 h-4 w-1 shrink-0 bg-accent' />;
}

export default function DesktopApp() {
  const t = useDict(DESKTOP_PAGE_TEXT);
  const { lang } = useLang();
  const storeUrl = MS_STORE_URL[lang];

  return (
    <div>
      <section className='chamfer mt-8 bg-surface-2 p-6 text-left shadow-[inset_0_0_0_1px_var(--color-line)]'>
        <div className='flex flex-wrap items-start justify-between gap-4'>
          <div>
            <h2 className='font-display text-xl font-bold'>{t.title}</h2>
            <p className='mt-2 max-w-[60ch] text-sm text-ink-2'>
              {t.intro[0]} <br />
              {t.intro[1]}
            </p>
          </div>

          <div className='flex shrink-0 flex-wrap gap-2.5'>
            {/* 같은 탭에서 열어야 새 창이 떴다 사라지지 않고 다운로드만 시작된다. */}
            <a data-umami-event='download-click' href={DOWNLOAD_URL} className={BUTTON}>
              {t.downloadExe}
            </a>
            <a
              data-umami-event='store-click-top'
              href={storeUrl}
              target='_blank'
              rel='noreferrer'
              className={BUTTON}
            >
              {t.downloadStore}
            </a>
          </div>
        </div>

        <ul className='mt-5 space-y-1.5 text-xs text-ink-3'>
          <li>{t.notes[0]}</li>
          <li>
            {t.fullscreenNote[0]}
            <strong className='text-ink-2'>{t.fullscreenNote[1]}</strong>
            {t.fullscreenNote[2]}
            <strong className='text-ink-2'>{t.fullscreenNote[3]}</strong>
            {t.fullscreenNote[4]}
            <strong className='text-ink-2'>{t.fullscreenNote[5]}</strong>
            {t.fullscreenNote[6]}
          </li>
          <li>{t.notes[1]}</li>
        </ul>
      </section>

      {/* 슬라이드는 판 없이 둔다. 이미지가 칸을 꽉 채워서 카드를 씌우면 상자만 하나 더 생긴다. */}
      <section className='mt-10 text-left'>
        <h2 className='font-display text-xl font-bold'>{t.howItWorks}</h2>
        <div className='mt-4'>
          <StoreSlides />
        </div>
      </section>

      <section className='chamfer mt-6 bg-surface-2 p-6 text-left shadow-[inset_0_0_0_1px_var(--color-line)]'>
        <h2 className='font-display text-xl font-bold'>{t.beforeDownload}</h2>

        <div className='mt-6 space-y-7'>
          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <Marker />
              {t.betaTitle}
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>
              {t.betaBody[0]} <br />
              {t.betaBody[1]} <br />
              {t.betaBody[2]}
            </p>
          </div>

          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <Marker />
              <span>
                {t.blockedTitle[0]}
                <strong className='text-accent'>{t.blockedTitle[1]}</strong>
                {t.blockedTitle[2]}
                <strong className='text-accent'>{t.blockedTitle[3]}</strong>
                {t.blockedTitle[4]}
              </span>
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>
              {t.blockedBody[0]}
              <strong className='text-ink'>{t.blockedBody[1]}</strong>
              {t.blockedBody[2]}
            </p>
            <ul className='mt-3 max-w-[62ch] space-y-1.5 text-sm text-ink-2'>
              <li>
                {t.blockedSteps[0][0]}
                <strong className='text-ink'>{t.blockedSteps[0][1]}</strong>
                {t.blockedSteps[0][2]}
                <strong className='text-ink'>{t.blockedSteps[0][3]}</strong>
              </li>
              <li>
                {t.blockedSteps[1][0]}
                <strong className='text-ink'>{t.blockedSteps[1][1]}</strong>
              </li>
            </ul>
            <p className='mt-3 max-w-[62ch] text-sm leading-relaxed text-ink-3'>{t.blockedNote}</p>
          </div>

          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <Marker />
              {t.storeTitle}
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>{t.storeBody}</p>
            <a
              data-umami-event='store-click-bottom'
              href={storeUrl}
              target='_blank'
              rel='noreferrer'
              className='mt-3 inline-block text-sm font-bold text-accent hover:underline'
            >
              {t.storeLink}
            </a>
          </div>
        </div>
      </section>
    </div>
  );
}
