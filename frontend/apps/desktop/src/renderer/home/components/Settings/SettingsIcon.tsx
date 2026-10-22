export function SettingsIcon({ size = 16 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox='0 0 24 24'
      fill='none'
      stroke='currentColor'
      strokeWidth='1.5'
      strokeLinejoin='round'
      aria-hidden='true'
    >
      <path d='M20.86 10.42 L20.86 13.58 L18.51 13.97 L18.00 15.21 L19.38 17.15 L17.15 19.38 L15.21 18.00 L13.97 18.51 L13.58 20.86 L10.42 20.86 L10.03 18.51 L8.79 18.00 L6.85 19.38 L4.62 17.15 L6.00 15.21 L5.49 13.97 L3.14 13.58 L3.14 10.42 L5.49 10.03 L6.00 8.79 L4.62 6.85 L6.85 4.62 L8.79 6.00 L10.03 5.49 L10.42 3.14 L13.58 3.14 L13.97 5.49 L15.21 6.00 L17.15 4.62 L19.38 6.85 L18.00 8.79 L18.51 10.03 Z' />
      <circle cx='12' cy='12' r='3' />
    </svg>
  );
}
