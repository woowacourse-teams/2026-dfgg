import { describe, expect, it } from 'vitest';

import { decideAugment, rankAugments, similarity } from './augmentMatch';

const AUGMENTS = [
  { augmentId: 1, names: ['벼락', 'Lightning Strikes'] },
  { augmentId: 2, names: ['포로 발사기', 'Poro Launcher'] },
  { augmentId: 3, names: ['흡혈병', 'Vampirism'] },
  { augmentId: 4, names: ['축소 엔진', 'Shrink Engine'] },
  { augmentId: 5, names: ['축소 광선', 'Shrink Ray'] },
];

const decide = (text: string) => decideAugment(rankAugments(text, AUGMENTS));

describe('증강 이름 맞추기', () => {
  it('띄어쓰기와 대소문자는 무시한다', () => {
    expect(similarity('포로발사기', '포로 발사기')).toBe(1);
    expect(similarity('poro LAUNCHER', 'Poro Launcher')).toBe(1);
  });

  it('OCR 이 그대로 읽으면 그 증강이다', () => {
    expect(decide('벼락')).toEqual({ kind: 'match', augmentId: 1 });
    expect(decide('Vampirism')).toEqual({ kind: 'match', augmentId: 3 });
  });

  it('한 글자를 틀려도 가장 비슷한 증강을 찾는다', () => {
    expect(decide('포로 발사가')).toEqual({ kind: 'match', augmentId: 2 });
    expect(decide('Shrink Engme')).toEqual({ kind: 'match', augmentId: 4 });
  });

  it('비슷한 이름이 둘이면 정하지 않고 후보로 넘긴다', () => {
    expect(decide('축소 광진')).toEqual({ kind: 'ambiguous', augmentIds: [4, 5] });
  });

  it('목록에 없는 글자나 빈 글자는 아무것도 아니다', () => {
    expect(decide('')).toEqual({ kind: 'none' });
    expect(decide('일단 대기')).toEqual({ kind: 'none' });
  });
});
