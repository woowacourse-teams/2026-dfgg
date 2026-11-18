import { isAutoLaunchEnabled } from '../autoLaunch/autoLaunch';
import { getDockingEnabled } from '../docking/docking';
import { getHighlightEnabled } from '../highlight/highlight';
import { getMaxGames } from '../highlight/storage';
import { isOverlayEnabled } from '../overlay/visibility';
import { getLocale } from '../settings';

export function settingSnapshot() {
  return {
    locale: getLocale(),
    autoLaunch: isAutoLaunchEnabled(),
    docking: getDockingEnabled(),
    showOverlay: isOverlayEnabled(),
    highlight: getHighlightEnabled(),
    maxGames: getMaxGames(),
  };
}
