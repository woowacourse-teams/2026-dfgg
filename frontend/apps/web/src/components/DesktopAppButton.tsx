import { useNavigate } from 'react-router-dom';

interface DesktopAppButtonProps {
  className?: string;
}

export default function DesktopAppButton({ className = '' }: DesktopAppButtonProps) {
  const navigate = useNavigate();

  return (
    <button
      type='button'
      onClick={() => navigate('/desktop-app')}
      className={`cursor-pointer rounded-xl bg-linear-to-br from-accent-deep to-accent-mid font-bold text-white transition-opacity hover:opacity-90 ${className}`}
    >
      데스크톱 앱
    </button>
  );
}
