/**
 * 개인정보처리방침 주소와 Riot 고지. 모든 페이지 하단에 같은 내용이 들어간다.
 *
 * 링크 문구를 "개인정보처리방침" 대신 주소 그대로 쓴다. Riot API 심사에 제출한
 * 주소와 화면에 보이는 주소가 같아야 확인이 쉽고, 사용자도 복사해 갈 수 있다.
 */
export const PRIVACY_URL = 'https://dfgg.pro/privacy';

/** 서비스를 처음 공개한 해. 해가 바뀌어도 그대로 둔다. */
const COPYRIGHT_YEAR = 2026;

export default function SiteFooter() {
  return (
    <footer className="mt-10 border-t border-line pt-6 text-left text-xs text-ink-3">
      <p>
        개인정보처리방침 {/* 같은 사이트 안이지만 절대 주소를 그대로 보여준다. */}
        <a href={PRIVACY_URL} className="text-ink-2 hover:text-hextech">
          {PRIVACY_URL}
        </a>
      </p>
      <p className="mt-3 leading-relaxed">
        dfgg는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다. League of Legends와 Riot Games는
        Riot Games, Inc.의 상표입니다.
      </p>
      {/* Riot 이 요구하는 고지문 원문. 의역하지 않고 그대로 둔다. */}
      <p className="mt-2 leading-relaxed">
        dfgg isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
        Riot Games or anyone officially involved in producing or managing Riot Games properties.
        Riot Games and all associated properties are trademarks or registered trademarks of Riot
        Games, Inc.
      </p>
      {/* 챔피언·아이템 이미지가 Riot 저작물이므로 출처를 함께 남긴다. */}
      <p className="mt-3">© {COPYRIGHT_YEAR} dfgg. League of Legends © Riot Games, Inc.</p>
    </footer>
  );
}
