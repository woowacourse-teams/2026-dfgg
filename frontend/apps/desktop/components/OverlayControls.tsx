import { useEffect, useState } from 'react';

/** 메인 창에 노출하는 배율 선택지. main.ts 의 OVERLAY_SCALES 와 맞춘다. */
const SCALES = [
  { value: 0.8, label: '작게' },
  { value: 1, label: '보통' },
  { value: 1.25, label: '크게' },
  { value: 1.5, label: '아주 크게' },
];

/**
 * 오버레이 표시 여부와 크기를 조절한다.
 *
 * 오버레이 자신은 클릭이 통과하도록 만들어져 있어 버튼을 달 수 없다.
 * 그래서 조작은 전부 메인 창에서 한다.
 */
export default function OverlayControls() {
  const [visible, setVisible] = useState(true);
  const [scale, setScale] = useState(1);
  const api = window.lcu?.overlay;

  useEffect(() => {
    if (!api) return;
    void api.getState().then((state) => {
      setVisible(state.visible);
      setScale(state.scale);
    });

    // 단축키로 껐다 켜면 메인 창 버튼도 따라가야 한다.
    return api.onState((state) => {
      setVisible(state.visible);
      setScale(state.scale);
    });
  }, [api]);

  if (!api) return null;

  return (
    <section className="mt-4 flex flex-wrap items-center gap-3 rounded border border-neutral-800 bg-neutral-900/60 p-3">
      <button
        type="button"
        onClick={() => void api.setVisible(!visible).then(setVisible)}
        aria-pressed={visible}
        className={`cursor-pointer rounded px-3 py-1.5 text-xs font-bold ${
          visible ? 'bg-emerald-500 text-emerald-950' : 'bg-neutral-700 text-neutral-300'
        }`}
      >
        오버레이 {visible ? '켜짐' : '꺼짐'}
      </button>

      <div className="flex items-center gap-1.5">
        <span className="text-xs text-neutral-400">크기</span>
        {SCALES.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => void api.setScale(option.value).then(setScale)}
            aria-pressed={scale === option.value}
            className={`cursor-pointer rounded px-2 py-1 text-xs ${
              scale === option.value
                ? 'bg-neutral-200 font-bold text-neutral-900'
                : 'bg-neutral-800 text-neutral-300 hover:bg-neutral-700'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      <span className="text-[11px] text-neutral-500">단축키 Alt+D 로도 켜고 끌 수 있어요</span>
    </section>
  );
}
