import { useNavigate } from 'react-router-dom';

interface DesktopAppButtonProps {
  className?: string;
  data: string;
}

export default function DesktopAppButton({ className = '', data }: DesktopAppButtonProps) {
  const navigate = useNavigate();

  return (
    <button
      data-umami-event={data}
      type='button'
      onClick={() => navigate('/desktop-app')}
      className={`cursor-pointer rounded-xl bg-linear-to-br from-accent-deep to-accent-mid font-bold text-white transition-opacity hover:opacity-90 ${className}`}
    >
      데스크톱 앱
    </button>
  );
}
