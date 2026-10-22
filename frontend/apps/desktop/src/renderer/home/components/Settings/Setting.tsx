import './Setting.css';

import { useEffect, useState } from 'react';

import { Locale } from '../../../../shared/i18n';
import { LOCALE, setLocale, t } from '../../../i18n';
import { BackIcon } from './BackIcon';
import { SettingRow, SettingSection } from './SettingSection';

export default function Setting({ onCloseSetting }: { onCloseSetting: () => void }) {
  const [autoLaunch, setAutoLaunch] = useState(false);
  const [autoDocking, setAutoDocking] = useState(false);
  const [overlayShow, setOverlayShow] = useState(false);

  useEffect(() => {
    window.settings.getAutoLaunch().then(setAutoLaunch);
    window.settings.getAutoDocking().then(setAutoDocking);
    window.settings.getOverlayShow().then(setOverlayShow);
  }, []);

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
            <input
              type='checkbox'
              className='setting-toggle'
              checked={autoLaunch}
              onChange={(e) => {
                setAutoLaunch(e.target.checked);
                window.settings.setAutoLaunch(e.target.checked);
              }}
            />
          </SettingRow>
        </SettingSection>
        <SettingSection title={t.setting.window}>
          <SettingRow label={t.setting.dockToClient} description={t.setting.dockToClientDesc}>
            <input
              type='checkbox'
              className='setting-toggle'
              checked={autoDocking}
              onChange={(e) => {
                setAutoDocking(e.target.checked);
                window.settings.setAutoDocking(e.target.checked);
              }}
            />
          </SettingRow>
        </SettingSection>
        <SettingSection title={t.setting.overlay}>
          <SettingRow label={t.setting.showOverlay}>
            <input
              type='checkbox'
              className='setting-toggle'
              checked={overlayShow}
              onChange={(e) => {
                setOverlayShow(e.target.checked);
                window.settings.setOverlayShow(e.target.checked);
              }}
            />
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
