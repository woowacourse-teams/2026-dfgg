import StoreSlides from '../../components/StoreSlides';

const MS_STORE_URL = 'https://apps.microsoft.com/detail/9nxl98m7xc82?hl=ko-KR&gl=KR';

/**
 * 최신 릴리스를 항상 가리킨다. 파일 이름에서 버전을 뺐기 때문에(artifactName)
 * 새 버전을 올려도 이 주소는 그대로다 — 릴리스마다 웹을 다시 배포할 필요가 없다.
 */
const DOWNLOAD_URL =
  'https://github.com/woowacourse-teams/2026-dfgg/releases/latest/download/dfgg-setup.exe';

export default function DesktopApp() {
  return (
    <div>
      <section className='chamfer mt-8 bg-surface-2 p-6 text-left shadow-[inset_0_0_0_1px_var(--color-line)]'>
        <div className='flex flex-wrap items-start justify-between gap-4'>
          <div>
            <h2 className='font-display text-xl font-bold'>데스크톱 앱</h2>
            <p className='mt-2 max-w-[60ch] text-sm text-ink-2'>
              열 명을 직접 입력하지 않아도 됩니다. <br />롤 클라이언트에서 조합을 자동으로 읽어 게임
              위에 추천 아이템을 띄워줍니다.
            </p>
          </div>

          {/* 같은 탭에서 열어야 새 창이 떴다 사라지지 않고 다운로드만 시작된다. */}
          <a
            data-umami-event='download-click'
            href={DOWNLOAD_URL}
            className='chamfer-sm shrink-0 bg-accent-strong px-5 py-3 font-display text-sm font-bold tracking-[0.12em] text-white uppercase transition-colors hover:bg-accent'
          >
            Windows 다운로드
          </a>
        </div>

        <ul className='mt-5 space-y-1.5 text-xs text-ink-3'>
          <li>
            내려받은 파일을 두 번 누르면 설치되고 바로 실행됩니다. 이후 새 버전이 나오면 앱이 알아서
            받아두고, 다시 시작할 때 적용됩니다.
          </li>
          <li>
            롤이 <strong className='text-ink-2'>전체 화면</strong>이면 오버레이가 게임에 가려집니다.
            롤 설정 → 그래픽 → 창 모드를 <strong className='text-ink-2'>테두리 없음</strong>이나{' '}
            <strong className='text-ink-2'>창 모드</strong>로 두세요.
          </li>
          <li>롤 클라이언트가 실행 중이어야 밴픽·게임 조합을 읽어옵니다.</li>
        </ul>
      </section>

      {/* 슬라이드는 판 없이 둔다. 이미지가 칸을 꽉 채워서 카드를 씌우면 상자만 하나 더 생긴다. */}
      <section className='mt-10 text-left'>
        <h2 className='font-display text-xl font-bold'>이렇게 동작합니다</h2>
        <div className='mt-4'>
          <StoreSlides />
        </div>
      </section>

      <section className='chamfer mt-6 bg-surface-2 p-6 text-left shadow-[inset_0_0_0_1px_var(--color-line)]'>
        <h2 className='font-display text-xl font-bold'>다운로드 하기 전에 확인해주세요</h2>

        <div className='mt-6 space-y-7'>
          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <span aria-hidden='true' className='mt-1.5 h-4 w-1 shrink-0 bg-accent' />
              아직 베타 버전이고, Riot Games의 승인을 받은 앱이 아닙니다
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>
              DFGG는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다. <br />
              저희가 직접 만들어 시험 삼아 공개한 베타 앱이라 예상치 못한 문제가 생길 수 있습니다.{' '}
              <br />
              Riot Games의 정책에 어긋난다는 판단이 서면 저희가 배포를 중단하겠습니다.
            </p>
          </div>

          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <span aria-hidden='true' className='mt-1.5 h-4 w-1 shrink-0 bg-accent' />
              다운로드가 막히면 <strong className='text-accent'>계속</strong> 또는{' '}
              <strong className='text-accent'>유지</strong>를 눌러주세요
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>
              코드 서명 인증서를 아직 준비하는 중이라, 브라우저와 Windows가 DFGG를 처음 보는 파일로
              취급해 경고를 띄웁니다. 바이러스가 발견된 게 아니라{' '}
              <strong className='text-ink'>누가 만들었는지 확인할 수 없다</strong>는 뜻입니다.
            </p>
            <ul className='mt-3 max-w-[62ch] space-y-1.5 text-sm text-ink-2'>
              <li>
                · 다운로드가 멈추면 브라우저 다운로드 목록에서{' '}
                <strong className='text-ink'>계속</strong> 또는{' '}
                <strong className='text-ink'>유지</strong>
              </li>
              <li>
                · 처음 실행할 때 파란 창이 뜨면{' '}
                <strong className='text-ink'>추가 정보 → 실행</strong>
              </li>
            </ul>
            <p className='mt-3 max-w-[62ch] text-sm leading-relaxed text-ink-3'>
              파일은 팀 GitHub 릴리스에서 그대로 내려받습니다. 인증서가 준비되면 이 단계는
              사라집니다.
            </p>
          </div>

          <div>
            <h3 className='flex items-start gap-2.5 font-display font-bold'>
              <span aria-hidden='true' className='mt-1.5 h-4 w-1 shrink-0 bg-accent' />
              Microsoft 스토어에서 받을 수 있습니다
            </h3>
            <p className='mt-2.5 max-w-[62ch] text-sm leading-relaxed text-ink-2'>
              Microsoft Store 버전은 검수를 거쳐 올라가기 때문에 위와 같은 경고 없이 설치되고, 새
              버전이 나오면 자동으로 갱신됩니다. 마이크로소프트 웹에서보다 스토어에서 다운받는 것을
              추천합니다.
            </p>
            {MS_STORE_URL && (
              <a
                data-umami-event='store-click'
                href={MS_STORE_URL}
                target='_blank'
                rel='noreferrer'
                className='mt-3 inline-block text-sm font-bold text-accent hover:underline'
              >
                Microsoft Store에서 받기 →
              </a>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
