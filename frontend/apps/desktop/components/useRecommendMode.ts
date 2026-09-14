import { useEffect, useState } from 'react';

export type RecommendMode = 1 | 2;

/**
 * 1번/2번 추천 방식 중 어느 걸 화면에 보여줄지.
 *
 * 오버레이는 클릭이 통과하는 창이라 스스로 버튼을 못 단다. 그래서 값은 메인
 * 프로세스가 들고 있고, 메인 창에서 바꾸면 메인 프로세스가 오버레이에도
 * 흘려보낸다. 두 창이 같은 훅을 써서 항상 같은 값을 보게 된다.
 */
export function useRecommendMode() {
  const [mode, setModeState] = useState<RecommendMode>(1);
  const api = window.lcu?.recommendMode;

  useEffect(() => {
    if (!api) return;
    void api.get().then(setModeState);
    return api.onChange(setModeState);
  }, [api]);

  const setMode = (next: RecommendMode) => {
    // 메인 창 버튼 클릭은 IPC 왕복을 기다리지 않고 바로 반영한다.
    setModeState(next);
    void api?.set(next);
  };

  return [mode, setMode] as const;
}
