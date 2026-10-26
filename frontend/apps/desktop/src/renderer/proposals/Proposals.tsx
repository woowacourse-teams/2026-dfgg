import { ChampSelect } from './ChampSelectProposals';
import { Counter, COUNTER_CASES } from './CounterProposals';
import { COUNTERS } from './data';
import { useIcons } from './icons';

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

const HOME_WIDTH = 448;

function Proposals() {
  const icons = useIcons();

  return (
    <main className='pp-page'>
      <header className='pp-page-head'>
        <img src='./icon.png' alt='' />
        <div>
          <h1>DFGG 디자인 시안</h1>
          <p>밴픽 화면: 오늘의 픽 · 룬 추천과 자동 적용. 홈 창 폭(480px) 그대로 그렸다.</p>
        </div>
        <nav>
          <a href='#champ-select'>밴픽 화면</a>
          <a href='#counter'>카운터</a>
          <a href='#counter-states'>카운터 상황</a>
          <a href='#states'>빈 상태</a>
        </nav>
      </header>

      <Section
        id='champ-select'
        title='1. 밴픽 화면 (최종)'
        description='밴픽이 시작되면 홈 창이 이 화면으로 바뀐다. 맨 위는 최근 대회에서 내 포지션 프로들이 고른 "오늘의 픽"(밴된 챔피언 제외, 매번 무작위 5개), 아래는 내 챔피언 룬 추천 3페이지. 선수 사진은 백엔드가 주는 Leaguepedia 직접 링크이고, 없거나 안 열리면 그 판 챔피언 그림으로 대신한다.'
        frameWidth={HOME_WIDTH}
        variants={[
          {
            label: '최종',
            title: '중계 자막 + 룬 탭',
            points: [
              '오늘의 픽: 선수 한 명을 크게(이름, 팀, 대회, 챔피언, 빌드), 아래 줄에서 5명을 넘긴다',
              '고른 선수의 룬 한 줄과 "이 룬 쓰기"로 그 룬을 바로 내 페이지에 넣는다',
              '룬 추천: 챔피언을 고르면 나오고 1순위가 바로 적용된다. 탭을 바꾸면 그 페이지가 바로 적용된다',
              '고른 탭이 곧 지금 들어간 룬이라 "적용됨" 같은 표시는 따로 두지 않는다',
            ],
            frame: <ChampSelect icons={icons} />,
          },
        ]}
      />

      <Section
        id='counter'
        title='2. 카운터 추천 (최종: 맞라인 상대 크게)'
        description='오늘의 픽과 룬 사이. 상대가 챔피언을 하나 공개할 때마다 /api/counters 를 다시 부른다. 상대 픽마다 이번 패치 라인 분포와 내 포지션 기준 카운터 최대 5명이 온다(아군·적·밴 제외). 내 라인으로 올 확률이 가장 높은 상대를 크게 보여 주고, 다른 상대는 알약 탭으로 바꿔 본다.'
        frameWidth={HOME_WIDTH}
        variants={[
          {
            label: '최종',
            title: '상대 3명 공개',
            points: [
              '맞라인 유력(내 라인 50% 이상) 상대를 전적 카드처럼 크게: 사선 그림 + 딱지 + 확률 빗금',
              '받아칠 챔피언은 번호 달린 큰 타일. 1순위만 레드 테',
              '아래 알약 탭으로 다른 상대를 본다. 내 라인 확률이 높은 순',
            ],
            frame: (
              <ChampSelect
                icons={icons}
                counter={<Counter enemies={COUNTER_CASES.many} patch={COUNTERS.patch} />}
              />
            ),
          },
        ]}
      />

      <Section
        id='counter-states'
        title='3. 카운터 상황별'
        description='밴픽이 진행되면서 같은 자리가 이렇게 바뀐다. 카운터 영역만 그렸다.'
        frameWidth={HOME_WIDTH}
        variants={[
          {
            label: '상황 1',
            title: '블루 1픽 · 상대 공개 전',
            points: ['enemies: [] 로 온다', '무엇을 기다리는지와 언제 바뀌는지만 알린다'],
            frame: <Counter enemies={COUNTER_CASES.blueFirst} patch={COUNTERS.patch} />,
          },
          {
            label: '상황 2',
            title: '상대 1명 공개',
            points: ['상대가 하나뿐이라 탭은 숨긴다'],
            frame: <Counter enemies={COUNTER_CASES.one} patch={COUNTERS.patch} />,
          },
          {
            label: '상황 3',
            title: '공개된 상대가 내 라인이 아님',
            points: [
              '내 라인 확률이 50% 아래면 "맞라인 유력" 대신 "정글 챔피언"처럼 주 라인을 적고 색을 뺀다',
              '카운터가 비면 이유를 한 줄로',
            ],
            frame: <Counter enemies={COUNTER_CASES.noLaner} patch={COUNTERS.patch} />,
          },
          {
            label: '상황 4',
            title: '카운터가 다섯보다 적음',
            points: [
              '아군·적·밴에 들어간 카운터를 빼고 채울 게 모자랄 때',
              '있는 만큼만 같은 크기로',
            ],
            frame: <Counter enemies={COUNTER_CASES.fewCounters} patch={COUNTERS.patch} />,
          },
          {
            label: '상황 5',
            title: '라인 집계 없음',
            points: ['새 챔피언이거나 patch 가 null', '확률 빗금을 빼고 "라인 정보 없음"'],
            frame: <Counter enemies={COUNTER_CASES.noPositions} patch={null} />,
          },
          {
            label: '상황 6',
            title: '내 챔피언 확정 후',
            points: [
              '고른 뒤에는 카운터가 필요 없어서 맞라인 한 줄로 접는다',
              '그만큼 룬 추천이 위로 올라온다',
            ],
            frame: <Counter enemies={COUNTER_CASES.many} patch={COUNTERS.patch} locked />,
          },
        ]}
      />

      <Section
        id='states'
        title='4. 룬을 못 보여 줄 때'
        description='오늘의 픽은 포지션만 있으면 나오므로 밴픽 시작부터 보인다. 룬은 챔피언을 골라야 나오고, 이번 패치 판 수가 100판보다 적으면 백엔드가 빈 목록을 준다.'
        frameWidth={HOME_WIDTH}
        variants={[
          {
            label: '상태',
            title: '챔피언 고르기 전',
            points: [
              '오늘의 픽을 보며 챔피언을 고를 수 있다',
              '룬 자리는 다음에 일어날 일만 알린다',
            ],
            frame: <ChampSelect icons={icons} state='noChampion' />,
          },
          {
            label: '상태',
            title: '판 수 부족',
            points: ['이유와 기준(100판)을 같이 적는다', '자동 적용은 하지 않는다'],
            frame: <ChampSelect icons={icons} state='fewGames' />,
          },
        ]}
      />
    </main>
  );
}

export default Proposals;
