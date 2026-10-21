import './Setting.css';

export default function Setting({ onCloseSetting }: { onCloseSetting: () => void }) {
  return (
    <div className='setting'>
      Setting입니다.
      <button className='setting-close-button' onClick={onCloseSetting}>
        닫기
      </button>
    </div>
  );
}
