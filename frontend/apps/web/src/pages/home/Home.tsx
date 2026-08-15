import { Link } from 'react-router-dom';

import banner from '../../assets/dfgg.png';

/**
 * 릴리스 에셋을 직접 가리킨다. GitHub 이 Content-Disposition: attachment 로
 * 내려주므로 페이지 이동 없이 바로 다운로드가 시작된다.
 *
 * 파일명에 버전이 들어 있어 릴리스를 올릴 때마다 이 상수를 바꿔야 한다.
 * 고정 링크를 원하면 에셋 이름을 dfgg.exe 처럼 버전 없이 올리고
 * .../releases/latest/download/dfgg.exe 를 쓰면 된다.
 */
const DOWNLOAD_VERSION = 'v1.0.0';
const DOWNLOAD_URL = `https://github.com/JuHyeong424/dfgg/releases/download/${DOWNLOAD_VERSION}/dfgg-1.0.0.exe`;

const ENTRIES = [
  // {
  //   to: '/nickname',
  //   title: '닉네임으로 추천받기',
  //   description: '소환사 이름을 입력하면 최근 전적을 바탕으로 아이템을 추천해드려요.',
  // },
  {
    to: '/champion-select',
    title: '챔피언 조합으로 추천받기',
    description: '지금 밴픽 중인 10명의 챔피언을 입력하면 아이템을 추천해드려요.',
  },
];

export default function Home() {
  return (
    <div className="min-h-screen bg-ground">
      <div className="relative aspect-5/1 overflow-hidden bg-cornflower">
        <img
          src={banner}
          alt="DFGG"
          className="absolute inset-0 h-full w-full object-cover object-[center_49%]"
        />
        <div className="banner-veil pointer-events-none absolute inset-0" />
      </div>

      <div className="mx-auto max-w-200 px-6 pt-11 pb-13 text-center">
        <h1 className="font-display text-4xl leading-none font-bold text-balance sm:text-6xl">
          다음 판, <em className="text-hextech not-italic">뭘 골라야</em> 이길까
        </h1>
        <p className="mx-auto mt-3 max-w-[42ch] text-ink-2 text-balance">
          어떤 방식으로 추천받을지 골라주세요.
        </p>

        <nav className="mt-9 grid gap-4 text-left sm:grid-cols-2">
          {ENTRIES.map((entry) => (
            <Link
              key={entry.to}
              to={entry.to}
              className="chamfer group bg-surface-2 p-6 shadow-[inset_0_0_0_1px_var(--color-line)] transition-shadow hover:shadow-[inset_0_0_0_1px_var(--color-hextech)]"
            >
              <h2 className="font-display text-xl font-bold transition-colors group-hover:text-hextech">
                {entry.title}
              </h2>
              <p className="mt-2 text-sm text-ink-2">{entry.description}</p>
            </Link>
          ))}
        </nav>

        <section className="chamfer mt-8 bg-surface-2 p-6 text-left shadow-[inset_0_0_0_1px_var(--color-line)]">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="font-display text-xl font-bold">데스크톱 앱</h2>
              <p className="mt-2 max-w-[46ch] text-sm text-ink-2">
                열 명을 직접 입력하지 않아도 됩니다. 롤 클라이언트에서 조합을 자동으로 읽어 게임
                위에 추천 아이템을 띄워줍니다.
              </p>
            </div>

            {/* 같은 탭에서 열어야 새 창이 떴다 사라지지 않고 다운로드만 시작된다. */}
            <a
              href={DOWNLOAD_URL}
              className="chamfer-sm shrink-0 bg-hextech px-5 py-3 font-display text-sm font-bold tracking-[0.12em] text-[#04231f] uppercase transition-colors hover:bg-hextech-bright"
            >
              Windows 다운로드
              <span className="ml-2 font-body text-[11px] font-normal tracking-normal normal-case opacity-70">
                {DOWNLOAD_VERSION}
              </span>
            </a>
          </div>

          <ul className="mt-5 space-y-1.5 text-xs text-ink-3">
            <li>
              설치 없이 실행됩니다. 처음 실행하면 Windows 보안 경고가 뜨는데{' '}
              <strong className="text-ink-2">추가 정보 → 실행</strong>을 눌러주세요. 코드 서명
              인증서를 준비 중입니다.
            </li>
            <li>
              롤 설정 → 그래픽 → 창 모드를 <strong className="text-ink-2">테두리 없음</strong>으로
              바꿔야 오버레이가 게임 위에 보입니다.
            </li>
            <li>롤 클라이언트가 실행 중이어야 밴픽·게임 조합을 읽어옵니다.</li>
          </ul>
        </section>
      </div>
    </div>
  );
}
