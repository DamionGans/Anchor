/**
 * Anchor - GitHub Pages Script
 * Features:
 * - Dynamic GitHub API release synchronizer (live version, APK asset link, size, date)
 *   with automatic fallback from DamionGans/Anchor to DamionGans/PulseMetronome
 * - Dynamic QR Code generator for direct phone downloads
 */

(function () {
  'use strict';

  // Fallback metadata from version.json
  const DEFAULT_METADATA = {
    version: '1.5.5',
    releaseTag: 'v1.5.5',
    downloadUrl: 'https://github.com/DamionGans/Anchor/releases/download/v1.5.5/Anchor-v1.5.5.apk',
    apkName: 'Anchor-v1.5.5.apk',
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
      const apkAsset = (releaseData.assets || []).find(a => a.name.endsWith('.apk'));
      const downloadUrl = apkAsset ? apkAsset.browser_download_url : `https://github.com/${activeRepo}/releases/download/${tag}/Anchor-${tag}.apk`;
      const sizeMb = apkAsset ? (apkAsset.size / (1024 * 1024)).toFixed(1) + ' MB' : DEFAULT_METADATA.fileSizeApprox;

      const pubDate = releaseData.published_at ? new Date(releaseData.published_at).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      }) : DEFAULT_METADATA.releaseDate;

      updateDownloadLinks(cleanVer, tag, downloadUrl, sizeMb, pubDate);
    } else {
      try {
        const localRes = await fetch('version.json');
        if (localRes.ok) {
          const localVer = await localRes.json();
          updateDownloadLinks(
            localVer.version,
            localVer.releaseTag || ('v' + localVer.version),
            localVer.downloadUrl,
            localVer.fileSizeApprox || DEFAULT_METADATA.fileSizeApprox,
            localVer.releaseDate || DEFAULT_METADATA.releaseDate
          );
          return;
        }
      } catch (e) {
        // Fallback to DEFAULT_METADATA
      }
      updateDownloadLinks(
        DEFAULT_METADATA.version,
        DEFAULT_METADATA.releaseTag,
        DEFAULT_METADATA.downloadUrl,
        DEFAULT_METADATA.fileSizeApprox,
        DEFAULT_METADATA.releaseDate
      );
    }
  }

  function updateDownloadLinks(versionName, releaseTag, url, size, date) {
    if (versionPill) {
      versionPill.textContent = releaseTag;
    }
    if (btnHeroDownload) {
      btnHeroDownload.href = url;
      const btnText = btnHeroDownload.querySelector('.btn-download-title');
      if (btnText) btnText.textContent = `Download for Android (${releaseTag})`;
    }
    if (btnNavDownload) {
      btnNavDownload.href = url;
      btnNavDownload.textContent = `Download (${releaseTag})`;
    }
    if (releaseDateSpan) {
      releaseDateSpan.textContent = date;
    }
    if (releaseSizeSpan) {
      releaseSizeSpan.textContent = size;
    }

    renderQrCode(url);
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
     3. QR Modal Event Listeners
     ========================================================================== */
  function initEvents() {
    if (btnShowQr && qrModal && btnCloseQr) {
      btnShowQr.addEventListener('click', (e) => {
        e.preventDefault();
        qrModal.classList.add('active');
      });

      btnCloseQr.addEventListener('click', () => {
        qrModal.classList.remove('active');
      });

      qrModal.addEventListener('click', (e) => {
        if (e.target === qrModal) {
          qrModal.classList.remove('active');
        }
      });
    }
  }

  initEvents();
  syncLatestRelease();
})();
