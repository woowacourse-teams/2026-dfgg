import './Setting.css';

import { useEffect, useState } from 'react';

import { Locale } from '../../../../shared/i18n';
import { LOCALE, setLocale, t } from '../../../i18n';
import { BackIcon } from './BackIcon';
import ConfirmModal from './ConfirmModal';
import { SettingRow, SettingSection } from './SettingSection';

const MAX_CLIPS_OPTIONS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];

export default function Setting({ onCloseSetting }: { onCloseSetting: () => void }) {
  const [autoLaunch, setAutoLaunch] = useState(false);
  const [autoDocking, setAutoDocking] = useState(false);
  const [overlayShow, setOverlayShow] = useState(false);
  const [highlight, setHighlight] = useState(false);
  const [maxClipCount, setMaxClipCount] = useState(5);
  const [pendingMaxClipCount, setPendingMaxClipCount] = useState<number | null>(null);

  const applyMaxClipCount = (n: number) => {
    setMaxClipCount(n);
    window.settings.setHighlightMaxClipCount(n);
  };

  useEffect(() => {
    window.settings.getAutoLaunch().then(setAutoLaunch);
    window.settings.getAutoDocking().then(setAutoDocking);
    window.settings.getOverlayShow().then(setOverlayShow);
    window.settings.getHighlightEnabled().then(setHighlight);
    window.settings.getHighlightMaxClipCount().then(setMaxClipCount);
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
            <input
              type='checkbox'
              className='setting-toggle'
              checked={highlight}
              onChange={(e) => {
                setHighlight(e.target.checked);
                window.settings.setHighlightEnabled(e.target.checked);
              }}
            />
          </SettingRow>
          <SettingRow label={t.setting.savePath} description={window.settings.getHighlightRoot()}>
            <div>
              <button
                type='button'
                className='setting-button'
                onClick={() => window.settings.openHighlightFolder()}
              >
                {t.setting.openFolder}
              </button>
            </div>
          </SettingRow>
          <SettingRow label={t.setting.maxClips} description={t.setting.maxClipsDesc}>
            <select
              className='setting-select'
              value={maxClipCount}
              onChange={(e) => {
                const next = Number(e.target.value);
                if (next < maxClipCount) setPendingMaxClipCount(next);
                else applyMaxClipCount(next);
              }}
            >
              {MAX_CLIPS_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {t.setting.clips(n)}
                </option>
              ))}
            </select>
          </SettingRow>
        </SettingSection>
        <SettingSection title={t.setting.about}>
          <SettingRow label={t.setting.version}>
            <span className='setting-value'>{window.settings.getAppVersion()}</span>
          </SettingRow>
          <button
            type='button'
            className='setting-link-row'
            onClick={() => window.settings.openPrivacyPolicy()}
          >
            {t.setting.privacyPolicy}
            <span aria-hidden='true'>↗</span>
          </button>
        </SettingSection>
        {/* Riot 이 요구하는 고지문 원문. 웹 SiteFooter 와 같고, 의역하지 않고 그대로 둔다. */}
        <p className='setting-disclaimer'>
          DFGG isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
          Riot Games or anyone officially involved in producing or managing Riot Games properties.
          Riot Games and all associated properties are trademarks or registered trademarks of Riot
          Games, Inc.
        </p>
      </div>
      <footer className='setting-footer'>
        <button type='button' className='setting-done-button' onClick={onCloseSetting}>
          {t.setting.done}
        </button>
      </footer>

      {pendingMaxClipCount !== null && (
        <ConfirmModal
          title={t.setting.maxClipsConfirmTitle}
          message={t.setting.maxClipsConfirmMessage(pendingMaxClipCount)}
          confirmLabel={t.setting.maxClipsConfirmOk}
          cancelLabel={t.setting.cancel}
          onConfirm={() => {
            window.analytics.track('settings-max-games-confirm', {
              result: 'ok',
              from: maxClipCount,
              to: pendingMaxClipCount,
            });
            applyMaxClipCount(pendingMaxClipCount);
            setPendingMaxClipCount(null);
          }}
          onCancel={() => {
            window.analytics.track('settings-max-games-confirm', {
              result: 'cancel',
              from: maxClipCount,
              to: pendingMaxClipCount,
            });
            setPendingMaxClipCount(null);
          }}
        />
      )}
    </div>
  );
}
