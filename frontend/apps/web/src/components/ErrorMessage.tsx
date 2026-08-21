import type { ReactNode } from 'react';

interface ErrorMessageProps {
  children: ReactNode;
  /** 앞뒤 여백은 쓰는 쪽 사정이라 밖에서 준다. */
  className?: string;
}

/**
 * 실패를 알리는 한 줄.
 *
 * role='alert' 을 컴포넌트가 들고 있는 게 핵심이다. 이걸 쓰는 쪽이 매번
 * 붙이게 두면 언젠가 빠지고, 그러면 스크린 리더 사용자는 실패했다는 사실을
 * 아예 듣지 못한 채 빈 화면을 보게 된다.
 */
export default function ErrorMessage({ children, className = '' }: ErrorMessageProps) {
  return (
    <p role='alert' className={`text-sm leading-relaxed text-loss ${className}`}>
      {children}
    </p>
  );
}
