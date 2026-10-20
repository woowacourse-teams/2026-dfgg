import { useItemIcons } from './icons';
import { OverlayA, OverlayB, OverlayC } from './OverlayProposals';
import { DetailA, DetailB, DetailC, PostGameA, PostGameB, PostGameC } from './RankProposals';

type Variant = { label: string; title: string; points: string[]; frame: React.ReactNode };

function Section({
  id,
  title,
  description,
  frameWidth,
  variants,
}: {
  id: string;
  title: string;
  description: string;
  frameWidth: number;
  variants: Variant[];
}) {
  return (
    <section className='pp-section' id={id}>
      <header className='pp-section-head'>
        <h2>{title}</h2>
        <p>{description}</p>
      </header>
      <div className='pp-variants'>
        {variants.map((variant) => (
          <article key={`${variant.label}-${variant.title}`} className='pp-variant'>
            <h3>
              <b>{variant.label}</b> {variant.title}
            </h3>
            <ul className='pp-points'>
              {variant.points.map((point) => (
                <li key={point}>{point}</li>
              ))}
            </ul>
            <div className='pp-frame' style={{ width: frameWidth }}>
              {variant.frame}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function Proposals() {
  const icon = useItemIcons();

  return (
    <main className='pp-page'>
      <header className='pp-page-head'>
        <img src='./icon.png' alt='' />
        <div>
          <h1>DFGG 디자인 시안</h1>
          <p>추천 v4 오버레이 · DF 스코어 순위 표시. 모두 실제 창 크기로 그렸다.</p>
        </div>
        <nav>
          <a href='#overlay'>오버레이</a>
          <a href='#postgame'>방금 끝난 판</a>
          <a href='#detail'>전적 상세</a>
        </nav>
      </header>

      <Section
        id='overlay'
        title='1. 오버레이: 하위 아이템과 상황템'
        description='추천 v4에서 코어마다 "지금 살 하위 아이템 묶음"과 상대 조합 때문에 고려할 "상황템(최대 2개)"이 더해진다. 폭 260px, 마우스를 올린 상태로 그렸다.'
        frameWidth={260}
        variants={[
          {
            label: 'A안',
            title: '지금 구조에 붙이기',
            points: [
              '1순위 카드 안에 "지금 살 것" 줄을 넣는다. 구매 순서대로 › 로 잇고 끝에 골드',
              '2·3순위는 이름 아래 작은 묶음',
              '상황템은 맨 아래 "상대 조합 대응"으로 분리',
            ],
            frame: <OverlayA icon={icon} />,
          },
          {
            label: 'B안',
            title: '지금 사기 우선',
            points: [
              '맨 위가 "지금 사야 할 재료". 번호 순서대로 사면 된다',
              '1순위 코어는 그 재료의 목적지로만 작게',
              '상황템은 "워윅 대응" 같은 한 줄 알림',
            ],
            frame: <OverlayB icon={icon} />,
          },
          {
            label: 'C안',
            title: '탭으로 나누기 (코어 탭)',
            points: [
              '코어 / 상황템을 탭으로 가른다. 한 번에 한 가지만 보여 가장 덜 붐빈다',
              '줄을 누르면 그 코어의 묶음이 펼쳐진다',
              '상황템은 탭을 눌러야 보여서 놓칠 수 있다',
            ],
            frame: <OverlayC icon={icon} tab='core' />,
          },
          {
            label: 'C안',
            title: '탭으로 나누기 (상황템 탭)',
            points: ['같은 C안의 상황템 탭', '살 수 없는 상황템은 흐리게, 모자란 골드를 표시'],
            frame: <OverlayC icon={icon} tab='situational' />,
          },
        ]}
      />

      <Section
        id='postgame'
        title='2. 방금 끝난 판: 하이라이트 위 DF 순위'
        description='게임이 끝나면 하이라이트 위에 이번 판 DF 스코어 등수를 보여 준다. 홈 창 폭 480px.'
        frameWidth={448}
        variants={[
          {
            label: 'A안',
            title: '큰 등수 하나',
            points: [
              '"3등 / 10명"이 가장 먼저 읽힌다',
              '점수, 우리 팀 내 순위, 승패를 옆에',
              '오른쪽 빗금 막대가 점수',
            ],
            frame: <PostGameA />,
          },
          {
            label: 'B안',
            title: '10명 줄 세우기',
            points: [
              '10명을 점수 순으로 세운다. 내 자리는 붉은 테와 "나"',
              '위아래 누구와 비슷한지 보인다',
              '팀은 테 색으로 구분',
            ],
            frame: <PostGameB />,
          },
          {
            label: 'C안',
            title: '칭호 카드',
            points: [
              '1등 MVP, 2·3등 TOP 3처럼 칭호로 부른다',
              '내 챔피언 그림을 사선으로 깔아 결과 화면처럼',
              '아래 열 칸 사다리에 내 위치',
            ],
            frame: <PostGameC />,
          },
        ]}
      />

      <Section
        id='detail'
        title='3. 전적 상세: 10명 DF 순위'
        description='전적 카드를 펼친 상세에서 10명 각각의 DF 등수를 보여 준다. 세 시안 모두 "내 플레이" 칸 맨 앞에 내 등수를 더했다.'
        frameWidth={448}
        variants={[
          {
            label: 'A안',
            title: '그림 위 등수 딱지',
            points: [
              '챔피언 그림 모서리에 등수. 1등 금색, 2·3등 흰색',
              '줄 구성은 지금 그대로',
              '점수 숫자는 마우스를 올려야 보인다',
            ],
            frame: <DetailA />,
          },
          {
            label: 'B안',
            title: '이름 옆 등수와 점수',
            points: ['이름 옆에 "3등 86"', '숫자까지 바로 읽힌다', '긴 이름은 더 일찍 잘린다'],
            frame: <DetailB />,
          },
          {
            label: 'C안',
            title: 'DF 순위 보기 추가',
            points: [
              '피해량 · 아이템 옆에 "DF 순위" 보기',
              '고르면 10명을 등수대로 한 줄씩, 점수는 빗금 막대',
              '평소 맞대결 화면은 A안처럼 딱지만',
            ],
            frame: <DetailC />,
          },
        ]}
      />
    </main>
  );
}

export default Proposals;
