import { t } from '../../../i18n';

type Props =
  | { kind: 'making'; done: number; total: number }
  | { kind: 'empty' }
  | { kind: 'deleted' }
  | { kind: 'loading' };

/**
 * 영상 대신 보이는 자리. 플레이어와 같은 크기의 판 위에 무슨 일인지와 다음에 무엇이 남는지를 적는다.
 * 만드는 중이면 클립 수만큼 칸을 나눠 다 만든 칸을 채운다.
 */
function HighlightNotice(props: Props) {
  if (props.kind === 'making') {
    const { done, total } = props;
    return (
      <div className='hl-notice hl-notice-making' role='status'>
        <span className='hl-tag'>
          <b>HIGHLIGHT</b>
          <span>{t.highlight.makingTag}</span>
        </span>
        <strong>{t.highlight.makingTitle}</strong>
        <small>{t.highlight.makingDesc(done, total)}</small>
        {total > 0 && (
          <span className='hl-notice-cells' aria-hidden='true'>
            {Array.from({ length: total }, (_, index) => (
              <i key={index} className={index < done ? 'hl-notice-cell-done' : ''} />
            ))}
          </span>
        )}
      </div>
    );
  }

  if (props.kind === 'loading') {
    return <div className='hl-notice' aria-busy='true' />;
  }

  const copy =
    props.kind === 'empty'
      ? { title: t.highlight.emptyTitle, desc: t.highlight.emptyDesc }
      : { title: t.highlight.deletedTitle, desc: t.highlight.deletedDesc };

  return (
    <div className='hl-notice'>
      <strong>{copy.title}</strong>
      <small>{copy.desc}</small>
    </div>
  );
}

export default HighlightNotice;
