import './Setting.css';

import { Locale } from '../../../../shared/i18n';
import { LOCALE, setLocale, t } from '../../../i18n';
import { BackIcon } from './BackIcon';
import { SettingRow, SettingSection } from './SettingSection';

export default function Setting({ onCloseSetting }: { onCloseSetting: () => void }) {
  return (
    <div className='setting'>
      <div className='setting-header'>
        <button type='button' className='setting-back' aria-label='뒤로' onClick={onCloseSetting}>
          <BackIcon />
        </button>
        <h1 className='setting-title'>{t.setting.title}</h1>
      </div>

      <div className='setting-content'>
        <SettingSection title={t.setting.general}>
          <SettingRow label={t.setting.language}>
            <select
              className='setting-select'
              defaultValue={LOCALE}
              onChange={(e) => {
                sessionStorage.setItem('reopenSetting', '1');
                setLocale(e.target.value as Locale);
              }}
            >
              <option value='ko'>한국어</option>
              <option value='en'>English</option>
            </select>
          </SettingRow>
          <SettingRow label={t.setting.autoLaunch}>
            <input type='checkbox' className='setting-toggle' />
          </SettingRow>
        </SettingSection>
        <SettingSection title={t.setting.overlay}>
          <SettingRow label={t.setting.showOverlay}>
            <input type='checkbox' />
          </SettingRow>
        </SettingSection>
        <SettingSection title={t.setting.highlight}>
          <SettingRow label={t.setting.autoSave} description={t.setting.autoSaveDesc}>
            <input type='checkbox' />
          </SettingRow>
          <SettingRow label={t.setting.savePath} description='저장 위치 경로'>
            <div>
              <button type='button'>{t.setting.openFolder}</button>
            </div>
          </SettingRow>
          <SettingRow label={t.setting.maxClips} description={t.setting.maxClipsDesc}>
            <select>
              <input />
            </select>
          </SettingRow>
        </SettingSection>
      </div>
      <footer className='setting-footer'>
        <button className='setting-save-button' onClick={onCloseSetting}>
          {t.setting.save}
        </button>
      </footer>
    </div>
  );
}
