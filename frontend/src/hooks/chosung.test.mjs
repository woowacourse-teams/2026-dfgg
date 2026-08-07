/**
 * 초성 변환 검증. 러너 없이 `node src/hooks/chosung.test.mjs`로 돌린다.
 * useChampions.ts의 toChosung/isChosungOnly를 그대로 옮겨 검증한다.
 * 원본을 고치면 아래 구현도 같이 고쳐야 한다.
 */
import assert from 'node:assert/strict';

const HANGUL_BASE = 0xac00;
const HANGUL_LAST = 0xd7a3;
const JUNG_JONG_COUNT = 21 * 28;
const CHOSUNG = [
  'ㄱ',
  'ㄲ',
  'ㄴ',
  'ㄷ',
  'ㄸ',
  'ㄹ',
  'ㅁ',
  'ㅂ',
  'ㅃ',
  'ㅅ',
  'ㅆ',
  'ㅇ',
  'ㅈ',
  'ㅉ',
  'ㅊ',
  'ㅋ',
  'ㅌ',
  'ㅍ',
  'ㅎ',
];

function toChosung(text) {
  let result = '';
  for (const char of text) {
    const code = char.charCodeAt(0);
    if (code >= HANGUL_BASE && code <= HANGUL_LAST) {
      result += CHOSUNG[Math.floor((code - HANGUL_BASE) / JUNG_JONG_COUNT)];
    } else {
      result += char;
    }
  }
  return result;
}

function isChosungOnly(text) {
  return text.length > 0 && [...text].every((char) => CHOSUNG.indexOf(char) !== -1);
}

assert.equal(toChosung('아리'), 'ㅇㄹ');
assert.equal(toChosung('진'), 'ㅈ');
assert.equal(toChosung('리 신'), 'ㄹ ㅅ');
assert.equal(toChosung('갱플랭크'), 'ㄱㅍㄹㅋ');
// 받침이 있어도 초성만 나와야 한다.
assert.equal(toChosung('알리스타'), 'ㅇㄹㅅㅌ');
// 쌍자음 초성
assert.equal(toChosung('뽀삐'), 'ㅃㅃ');
// 한글이 아닌 글자는 보존
assert.equal(toChosung('Jinx'), 'Jinx');

assert.equal(isChosungOnly('ㅇㄹ'), true);
assert.equal(isChosungOnly('아리'), false);
assert.equal(isChosungOnly('jin'), false);
assert.equal(isChosungOnly(''), false);
// 초성과 완성형이 섞이면 초성 검색이 아니다.
assert.equal(isChosungOnly('ㅇ리'), false);

// 실제 검색 시나리오: "ㅇㄹ"로 아리가 걸리고 알리스타도 걸린다.
const names = ['아리', '알리스타', '진', '갱플랭크'];
const hits = names.filter((n) => toChosung(n).includes('ㅇㄹ'));
assert.deepEqual(hits, ['아리', '알리스타']);

// 실패하면 assert가 던진다. 여기까지 예외 없이 도달하면 통과.
