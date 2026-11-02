/**
 * Anchor - GitHub Pages Script
 * Features:
 * - Dynamic GitHub API release synchronizer (live version, APK + Windows MSI links, size, date)
 *   with automatic fallback from DamionGans/Anchor to DamionGans/PulseMetronome
 * - Client OS detection (Windows x64 / ARM64, Android, iOS) with smart download targeting
 * - Dynamic QR Code generator for direct phone downloads
 */

(function () {
  'use strict';

  // Fallback metadata from version.json
  const DEFAULT_METADATA = {
    version: '1.6.1',
    releaseTag: 'v1.6.1',
    downloadUrl: 'https://github.com/DamionGans/Anchor/releases/download/v1.6.1/Anchor-v1.6.1.apk',
    apkName: 'Anchor-v1.6.1.apk',
    fileSizeApprox: '~2.1 MB',
    releaseDate: 'November 2026',
    repoUrl: 'https://github.com/DamionGans/Anchor'
  };

  // Release sync elements
  const versionPill = document.getElementById('version-pill-text');
  const btnHeroDownload = document.getElementById('hero-download-btn');
  const btnNavDownload = document.getElementById('nav-download-btn');
  const releaseDateSpan = document.getElementById('release-date-text');
  const releaseSizeSpan = document.getElementById('release-size-text');
  const qrModal = document.getElementById('qr-modal');
  const btnShowQr = document.getElementById('btn-show-qr');
  const btnCloseQr = document.getElementById('btn-close-qr');
  const qrContainer = document.getElementById('qr-container');
  const winX64Link = document.getElementById('win-x64-download');
  const winArm64Link = document.getElementById('win-arm64-download');
  const androidCardLink = document.getElementById('android-card-download');

  const chipWindows = document.getElementById('chip-windows');
  const chipAndroid = document.getElementById('chip-android');
  const chipIos = document.getElementById('chip-ios');

  function detectClientOS() {
    const userAgent = navigator.userAgent || '';
    const platform = navigator.platform || '';
    if (/Win/i.test(platform) || /Windows/i.test(userAgent)) {
      const isArm = /ARM/i.test(userAgent) || /ARM64/i.test(platform);
      return isArm ? 'windows-arm64' : 'windows-x64';
    }
    if (/Android/i.test(userAgent)) return 'android';
    if (/iPhone|iPad|iPod/i.test(userAgent) || (/Mac/i.test(platform) && navigator.maxTouchPoints > 1)) {
      return 'ios';
    }
    if (/Mac/i.test(platform)) return 'macos';
    if (/Linux/i.test(platform)) return 'linux';
    return 'unknown';
  }

  /* ==========================================================================
     1. GitHub Releases Live Synchronization
     ========================================================================== */
  async function syncLatestRelease() {
    const REPOS = ['DamionGans/Anchor', 'DamionGans/PulseMetronome'];
    const CACHE_KEY = 'anchor_latest_release_v1';
    const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

    let releaseData = null;
    let activeRepo = REPOS[0];

    try {
      const cached = sessionStorage.getItem(CACHE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Date.now() - parsed.timestamp < CACHE_TTL_MS) {
          releaseData = parsed.data;
          activeRepo = parsed.repo || REPOS[0];
        }
      }

      if (!releaseData) {
        for (const repo of REPOS) {
          const response = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, {
            headers: { 'Accept': 'application/vnd.github.v3+json' }
          });
          if (response.ok) {
            releaseData = await response.json();
            activeRepo = repo;
            sessionStorage.setItem(CACHE_KEY, JSON.stringify({
              timestamp: Date.now(),
              repo: activeRepo,
              data: releaseData
            }));
            break;
          }
        }
      }
    } catch (err) {
      console.warn('GitHub API fetch bypassed, using local version fallback:', err);
    }

    if (releaseData && releaseData.tag_name) {
      const tag = releaseData.tag_name;
      const cleanVer = tag.replace(/^v/, '');
      const assets = releaseData.assets || [];
      const apkAsset = assets.find(a => a.name.endsWith('.apk'));
      const downloadUrl = apkAsset ? apkAsset.browser_download_url : `https://github.com/${activeRepo}/releases/download/${tag}/Anchor-${tag}.apk`;
      const sizeMb = apkAsset ? (apkAsset.size / (1024 * 1024)).toFixed(1) + ' MB' : DEFAULT_METADATA.fileSizeApprox;

      const pubDate = releaseData.published_at ? new Date(releaseData.published_at).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      }) : DEFAULT_METADATA.releaseDate;

      updateAllPlatformLinks(cleanVer, tag, downloadUrl, sizeMb, pubDate, assets);
    } else {
      let ver = DEFAULT_METADATA.version;
      let tag = DEFAULT_METADATA.releaseTag;
      let url = DEFAULT_METADATA.downloadUrl;
      let size = DEFAULT_METADATA.fileSizeApprox;
      let date = DEFAULT_METADATA.releaseDate;

      try {
        const localRes = await fetch('version.json');
        if (localRes.ok) {
          const localVer = await localRes.json();
          ver = localVer.version || ver;
          tag = localVer.releaseTag || ('v' + ver);
          url = localVer.downloadUrl || url;
          size = localVer.fileSizeApprox || size;
          date = localVer.releaseDate || date;
        }
      } catch (e) {
        // Fallback to DEFAULT_METADATA
      }
      updateAllPlatformLinks(ver, tag, url, size, date, []);
    }
  }

  function updateAllPlatformLinks(versionName, releaseTag, apkUrl, size, date, assets) {
    if (versionPill) {
      versionPill.textContent = releaseTag;
    }
    if (releaseDateSpan) {
      releaseDateSpan.textContent = date;
    }
    if (releaseSizeSpan) {
      releaseSizeSpan.textContent = size;
    }

    // Windows MSI links
    const x64Asset = assets.find(a => a.name.endsWith('windows-x64.msi'));
    const arm64Asset = assets.find(a => a.name.endsWith('windows-arm64.msi'));
    const defaultReleaseUrl = `https://github.com/DamionGans/Anchor/releases/tag/${releaseTag}`;

    const x64Url = x64Asset ? x64Asset.browser_download_url : (winX64Link ? winX64Link.href : defaultReleaseUrl);
    const arm64Url = arm64Asset ? arm64Asset.browser_download_url : (winArm64Link ? winArm64Link.href : defaultReleaseUrl);

    if (winX64Link) winX64Link.href = x64Url;
    if (winArm64Link) winArm64Link.href = arm64Url;
    if (androidCardLink) androidCardLink.href = apkUrl;

    const os = detectClientOS();

    // Highlight the relevant platform chip
    if (chipWindows) chipWindows.classList.remove('active');
    if (chipAndroid) chipAndroid.classList.remove('active');
    if (chipIos) chipIos.classList.remove('active');

    if (btnHeroDownload) {
      const btnTitle = btnHeroDownload.querySelector('.btn-download-title');
      if (os === 'windows-x64' || os === 'windows-arm64') {
        if (chipWindows) chipWindows.classList.add('active');
        btnHeroDownload.href = os === 'windows-arm64' ? arm64Url : x64Url;
        if (btnTitle) {
          btnTitle.textContent = `Download for Windows (${releaseTag})`;
        }
        if (btnNavDownload) {
          btnNavDownload.href = btnHeroDownload.href;
          btnNavDownload.textContent = `Download for Windows (${releaseTag})`;
        }
      } else if (os === 'ios') {
        if (chipIos) chipIos.classList.add('active');
        btnHeroDownload.href = '#platforms-ios';
        if (btnTitle) {
          btnTitle.textContent = `Anchor for iPhone (iOS 16+)`;
        }
        if (btnNavDownload) {
          btnNavDownload.href = '#platforms-ios';
          btnNavDownload.textContent = `Get for iPhone`;
        }
      } else {
        // Android or other
        if (chipAndroid) chipAndroid.classList.add('active');
        btnHeroDownload.href = apkUrl;
        if (btnTitle) {
          btnTitle.textContent = `Download for Android (${releaseTag})`;
        }
        if (btnNavDownload) {
          btnNavDownload.href = apkUrl;
          btnNavDownload.textContent = `Download (${releaseTag})`;
        }
      }
    }

    renderQrCode(apkUrl);
  }

  /* ==========================================================================
     2. Lightweight QR Code Generator (SVG)
     ========================================================================== */
  function renderQrCode(url) {
    if (!qrContainer) return;

    const encodedUrl = encodeURIComponent(url);
    const qrImg = document.createElement('img');
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodedUrl}&format=svg`;
    qrImg.alt = 'Scan to download Anchor APK';
    qrImg.width = 160;
    qrImg.height = 160;
    qrImg.style.borderRadius = '8px';

    qrContainer.innerHTML = '';
    qrContainer.appendChild(qrImg);
  }

  /* ==========================================================================
     3. Modal & Button Event Listeners
     ========================================================================== */
  function initEvents() {
    function openQrModal(e) {
      if (e) e.preventDefault();
      if (qrModal) qrModal.classList.add('active');
    }

    function closeQrModal() {
      if (qrModal) qrModal.classList.remove('active');
    }

    if (btnShowQr) {
      btnShowQr.addEventListener('click', openQrModal);
    }

    document.querySelectorAll('.btn-card-qr').forEach(btn => {
      btn.addEventListener('click', openQrModal);
    });

    if (btnCloseQr) {
      btnCloseQr.addEventListener('click', closeQrModal);
    }

    if (qrModal) {
      qrModal.addEventListener('click', (e) => {
        if (e.target === qrModal) {
          closeQrModal();
        }
      });
    }
  }

  initEvents();
  syncLatestRelease();
})();
