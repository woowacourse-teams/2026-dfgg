import { summaryUrl } from '../api';
import BarList from '../components/BarList';
import EventTable from '../components/EventTable';
import Funnel from '../components/Funnel';
import KpiTile from '../components/KpiTile';
import PageMeta from '../components/PageMeta';
import Panel, { Unavailable } from '../components/Panel';
import RateCard from '../components/RateCard';
import SourceNotice, { RequestError } from '../components/SourceNotice';
import TrendChart, { type TrendSeries } from '../components/TrendChart';
import { countryName, formatDateTime, formatDuration, formatPercent, shortDate } from '../format';
import {
  type FunnelStep,
  hasError,
  SOURCE_NAME,
  type StoreFunnel,
  type StoreSeries,
  type Summary,
} from '../types';
import { useJson } from '../useJson';

const DAY = 86_400_000;
const KST = 9 * 3_600_000;

const kstDate = (ms: number) => new Date(ms + KST).toISOString().slice(0, 10);

function dateList(startAt: number, endAt: number) {
  const dates: string[] = [];
  for (let t = startAt; t <= endAt; t += DAY) dates.push(kstDate(t));
  return dates;
}

const seriesOf = (s: StoreSeries | { error: string }) => (hasError(s) ? null : s);

export default function Dashboard({ days, reloadKey }: { days: number; reloadKey: number }) {
  const { data: summary, error, loading } = useJson<Summary>(summaryUrl(days), reloadKey);

  const analytics = summary?.analytics.ok ? summary.analytics.data : null;
  const sourceName = summary ? SOURCE_NAME[summary.source] : '분석 도구';
  const store = summary?.store.ok ? summary.store.data : null;
  const acquisitions = store ? seriesOf(store.acquisitions) : null;
  const installs = store ? seriesOf(store.installs) : null;
  const storeViews = store?.pageviews ? seriesOf(store.pageviews) : null;
  // Partner Center 차트 CSV 는 주 단위라 일별 차트에 섞지 않는다.
  const storeWeekly = acquisitions?.granularity === 'week';
  const storeInstalls = installs ?? acquisitions;
  const storeFunnel: FunnelStep[] = store?.funnel
    ? [
        ['pageviews', '스토어 페이지 조회'],
        ['attempts', '설치 시도'],
        ['installs', '설치 성공'],
        ['firstLaunch', '스토어에서 바로 실행'],
      ].map(([key, label]) => ({
        key,
        label,
        unit: 'visitors' as const,
        events: [],
        value: store.funnel?.[key as keyof StoreFunnel] ?? null,
      }))
    : [];
  const weekLabel = (date: string) => `${shortDate(date)} 주`;

  const dates = summary ? dateList(summary.range.startAt, summary.range.endAt) : [];
  const byDate = <T,>(rows: T[] | undefined, key: (row: T) => string) =>
    new Map((rows ?? []).map((row) => [key(row), row]));
  const dailyByDate = byDate(analytics?.daily, (row) => row.date);
  const storeDays = byDate(acquisitions?.daily, (row) => row.date);
  // 스토어 집계는 며칠 늦게 들어온다 — 마지막으로 들어온 날 이후는 0이 아니라 빈칸으로 둔다.
  const storeLastDate = acquisitions?.daily[acquisitions.daily.length - 1]?.date ?? '';

  const trend: TrendSeries[] = [
    {
      key: 'sessions',
      label: '웹 방문',
      color: 'var(--color-series-1)',
      values: dates.map((d) => (analytics ? (dailyByDate.get(d)?.sessions ?? 0) : null)),
    },
    {
      key: 'launches',
      label: '데스크탑 앱 실행',
      color: 'var(--color-series-2)',
      values: dates.map((d) => (analytics ? (dailyByDate.get(d)?.launches ?? 0) : null)),
    },
    ...(storeWeekly
      ? []
      : [
          {
            key: 'acquisitions',
            label: '스토어 획득',
            color: 'var(--color-series-3)',
            values: dates.map((d) =>
              acquisitions && d <= storeLastDate ? (storeDays.get(d)?.count ?? 0) : null,
            ),
          },
        ]),
  ];

  return (
    <div className='space-y-5'>
      <PageMeta generatedAt={summary?.range.generatedAt} mock={summary?.mock} loading={loading} />
      {error && <RequestError message={error} />}

      {summary && (
        <>
          <SourceNotice name={sourceName} source={summary.analytics} />
          <SourceNotice name='Microsoft Store' source={summary.store} />

          <div className='grid grid-cols-2 gap-3 lg:grid-cols-5'>
            <KpiTile
              label='웹 방문자'
              value={analytics?.web.visitors ?? null}
              prev={analytics?.web.prev.visitors}
            />
            <KpiTile
              label='웹 페이지뷰'
              value={analytics?.web.pageviews ?? null}
              prev={analytics?.web.prev.pageviews}
              hint={
                analytics
                  ? `평균 체류 ${formatDuration(analytics.web.totaltime / Math.max(analytics.web.visits, 1))}`
                  : undefined
              }
            />
            <KpiTile
              label='데스크탑 활성 사용자'
              value={analytics?.desktop.visitors ?? null}
              prev={analytics?.desktop.prev.visitors}
            />
            {storeViews ? (
              <>
                <KpiTile
                  label='스토어 페이지 조회'
                  value={storeViews.total}
                  prev={storeViews.prevTotal}
                  hint={storeWeekly ? '주 단위 집계를 기간에 맞춘 근사치' : undefined}
                />
                <KpiTile
                  label='스토어 설치'
                  value={storeInstalls?.total ?? null}
                  prev={storeInstalls?.prevTotal}
                  hint={
                    storeInstalls && storeViews.total > 0
                      ? `페이지 조회 대비 ${formatPercent(storeInstalls.total / storeViews.total)}`
                      : undefined
                  }
                />
              </>
            ) : (
              <>
                <KpiTile
                  label='스토어 획득'
                  value={acquisitions?.total ?? null}
                  prev={acquisitions?.prevTotal}
                />
                <KpiTile
                  label='스토어 설치'
                  value={installs?.total ?? null}
                  prev={installs?.prevTotal}
                />
              </>
            )}
          </div>

          <Panel
            title='일별 추이'
            description={
              storeWeekly
                ? '웹 방문은 세션 수, 앱 실행은 실행 이벤트 수. 스토어는 주 단위라 아래 Microsoft Store 칸에 따로 표시'
                : acquisitions?.freshness
                  ? `스토어 데이터는 ${formatDateTime(Date.parse(acquisitions.freshness))}까지 반영 (보통 1~3일 지연)`
                  : '웹 방문은 세션 수, 앱 실행은 실행 이벤트 수'
            }
          >
            <TrendChart dates={dates} series={trend} />
          </Panel>

          <div className='grid gap-5 lg:grid-cols-5'>
            <Panel
              className='lg:col-span-3'
              title='전환 흐름'
              description='각 단계의 순 방문자 수, 화살표는 앞 단계 대비 비율. 이름에 마우스를 올리면 집계 이벤트가 보입니다.'
            >
              <Funnel steps={summary.funnel} />
            </Panel>

            <Panel
              className='lg:col-span-2'
              title='추천 성공률'
              description='추천 요청 이벤트 기준'
            >
              {analytics ? (
                <div className='space-y-6'>
                  <RateCard label='웹' rate={analytics.rates.web} />
                  <RateCard label='데스크탑 앱' rate={analytics.rates.desktop} />
                </div>
              ) : (
                <Unavailable>{sourceName} 연결 필요</Unavailable>
              )}
            </Panel>
          </div>

          {store && (storeFunnel.length > 0 || storeWeekly) && (
            <div className='grid gap-5 lg:grid-cols-2'>
              {storeFunnel.length > 0 && (
                <Panel
                  title='Microsoft Store 퍼널'
                  description='Partner Center 에서 내려받은 기간 전체. 마지막 단계는 설치 직후 스토어에서 바로 실행한 수'
                >
                  <Funnel steps={storeFunnel} />
                </Panel>
              )}
              {storeWeekly && storeInstalls && (
                <Panel
                  title='주별 스토어 설치'
                  description={
                    storeInstalls.freshness
                      ? `${formatDateTime(Date.parse(storeInstalls.freshness))}까지 반영 · 날짜는 그 주의 월요일`
                      : '날짜는 그 주의 월요일'
                  }
                >
                  <BarList
                    items={[...storeInstalls.daily]
                      .reverse()
                      .map(({ date, count }) => ({ x: weekLabel(date), y: count }))}
                    empty='이 기간에 해당하는 주가 없습니다'
                  />
                </Panel>
              )}
            </div>
          )}

          <div className='grid gap-5 md:grid-cols-2 lg:grid-cols-4'>
            <Panel title='유입 경로'>
              {analytics ? (
                <BarList items={analytics.top.referrers} />
              ) : (
                <Unavailable>—</Unavailable>
              )}
            </Panel>
            <Panel title='많이 본 페이지'>
              {analytics ? <BarList items={analytics.top.paths} /> : <Unavailable>—</Unavailable>}
            </Panel>
            <Panel title='웹 방문 국가'>
              {analytics ? (
                <BarList items={analytics.top.countries} label={countryName} />
              ) : (
                <Unavailable>—</Unavailable>
              )}
            </Panel>
            <Panel
              title='스토어 설치 국가'
              description={store?.marketsWholePeriod ? '내려받은 기간 전체' : undefined}
            >
              {store && !hasError(store.markets) ? (
                <BarList items={store.markets} label={countryName} />
              ) : (
                <Unavailable>
                  {store && hasError(store.markets) ? store.markets.error : 'Store 연결 필요'}
                </Unavailable>
              )}
            </Panel>
          </div>

          <Panel
            title='이벤트'
            description='desktop- 으로 시작하는 이벤트는 데스크탑 앱에서 보낸 것입니다.'
          >
            {analytics ? (
              <EventTable events={analytics.events} />
            ) : (
              <Unavailable>{sourceName} 연결 필요</Unavailable>
            )}
          </Panel>
        </>
      )}
    </div>
  );
}
