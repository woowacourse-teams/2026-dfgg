import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { PRIVACY_URL } from '../../components/SiteFooter';

const LAST_UPDATED = '2026년 8월 21일';

const CONTACT_EMAIL = 'dfgg0821@gmail.com';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className='mt-10'>
      <h2 className='font-display text-xl font-bold'>{title}</h2>
      <div className='mt-3 space-y-3 text-sm leading-relaxed text-ink-2'>{children}</div>
    </section>
  );
}

export default function Privacy() {
  return (
    <div className='min-h-screen bg-ground'>
      <div className='mx-auto max-w-180 px-6 pt-11 pb-16'>
        <Link to='/' className='font-display text-sm tracking-wider text-ink-3 hover:text-accent'>
          ← DFGG
        </Link>

        <h1 className='mt-6 font-display text-4xl leading-none font-bold'>개인정보처리방침</h1>
        <p className='mt-3 text-sm text-ink-3'>
          {PRIVACY_URL} · 최종 수정일: {LAST_UPDATED}
        </p>

        <p className='mt-6 text-sm leading-relaxed text-ink-2'>
          본 방침은 DFGG 웹사이트(https://dfgg.pro)와 DFGG 데스크톱 앱에 모두 적용됩니다.
        </p>

        <Section title='1. 수집하지 않는 정보'>
          <p>
            DFGG는 회원가입과 로그인이 없습니다. 이름, 이메일, 소환사명, Riot ID, PUUID, 결제 정보를
            수집하거나 저장하지 않습니다. 광고 식별자는 사용하지 않습니다.
          </p>
        </Section>

        <Section title='2. 처리하는 정보'>
          <p>
            아이템 추천을 위해 한 경기에 등장하는 챔피언 10개의 이름과 포지션을 서버로 전송합니다.
            이 정보만으로는 개인을 식별할 수 없습니다.
          </p>
          <p>
            데스크톱 앱은 사용자의 PC에서 실행 중인 League of Legends 클라이언트로부터 로컬
            주소(127.0.0.1)를 통해 경기 정보를 읽습니다. 이 과정에서 조회되는 소환사명은 10명 중
            사용자 본인을 구분하기 위해 PC 안에서만 사용되며 외부로 전송되지 않습니다.
          </p>
        </Section>

        <Section title='3. 자동으로 기록되는 정보'>
          <p>
            서버 운영과 장애 대응을 위해 웹 서버 접속 기록(IP 주소, 접속 시각, 요청 경로)이
            남습니다. 이 기록은 통계나 마케팅에 사용하지 않습니다.
          </p>
          <p>
            또한 서비스 개선을 위해 Google Analytics 를 사용합니다. 방문한 페이지와 버튼 클릭 등
            사용 기록이 쿠키를 통해 수집되며, 개인을 식별할 수 있는 정보는 수집하지 않습니다.
            마케팅이나 광고에는 사용하지 않습니다.
          </p>
        </Section>

        <Section title='4. 제3자 서비스'>
          <p>
            챔피언 및 아이템 이미지는 Riot Games가 운영하는 Data Dragon(ddragon.leagueoflegends.com)
            에서 직접 불러옵니다.
          </p>
          <p>
            사용 기록 집계를 위해 Google Analytics 를 사용합니다. 수집된 정보는 Google LLC(미국)로
            이전되어 처리되며, 해당 처리는 Google 의 개인정보처리방침
            (https://policies.google.com/privacy)을 따릅니다.
          </p>
        </Section>

        <Section title='5. 문의'>
          <p>
            개인정보 관련 문의:{' '}
            <a href={`mailto:${CONTACT_EMAIL}`} className='text-accent hover:underline'>
              {CONTACT_EMAIL}
            </a>
          </p>
        </Section>

        <Section title='6. 고지'>
          <p>DFGG는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다.</p>
          {/* Riot 이 요구하는 고지문 원문. 의역하지 않고 그대로 둔다. */}
          <p className='text-ink-3'>
            DFGG isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
            Riot Games or anyone officially involved in producing or managing Riot Games properties.
            Riot Games and all associated properties are trademarks or registered trademarks of Riot
            Games, Inc.
          </p>
          <p className='text-ink-3'>&copy; 2026 DFGG. League of Legends &copy; Riot Games, Inc.</p>
        </Section>
      </div>
    </div>
  );
}
