# 히어로 제목 글꼴

`cafe24-ohsquare-headline.woff2`는 카페24 아네모네 오스퀘어(Cafe24Ohsquare)에서 히어로 제목 글자만 남긴 파일입니다.
제목 문구(`apps/web/src/i18n/i18n.ts`의 `hero.headline`)를 바꾸면 아래 명령으로 다시 뽑습니다. `--text`에는 한국어·영어 제목을 모두 넣습니다.

```sh
curl -sL -o /tmp/Cafe24Ohsquare.woff https://cdn.jsdelivr.net/gh/projectnoonnu/noonfonts_2001@1.1/Cafe24Ohsquare.woff
pip install fonttools brotli
pyftsubset /tmp/Cafe24Ohsquare.woff \
  --text="이젠 물어보지 마세요Stop asking." \
  --flavor=woff2 --layout-features='*' \
  --output-file=apps/web/src/assets/fonts/cafe24-ohsquare-headline.woff2
```
