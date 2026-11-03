# Generates Google Play Store phone & tablet screenshots using System.Drawing
Add-Type -AssemblyName System.Drawing

$imagesDir = "fastlane/metadata/android/en-US/images"
$phoneDir = "$imagesDir/phoneScreenshots"
$sevenDir = "$imagesDir/sevenInchScreenshots"

New-Item -ItemType Directory -Force -Path $phoneDir | Out-Null
New-Item -ItemType Directory -Force -Path $sevenDir | Out-Null

$bgDark = [System.Drawing.Color]::FromArgb(8, 12, 20)
$cardBg = [System.Drawing.Color]::FromArgb(14, 20, 32)
$surfaceBg = [System.Drawing.Color]::FromArgb(20, 30, 48)
$cyan = [System.Drawing.Color]::FromArgb(0, 229, 255)
$cyanGlow = [System.Drawing.Color]::FromArgb(40, 0, 229, 255)
$white = [System.Drawing.Color]::FromArgb(255, 255, 255)
$silver = [System.Drawing.Color]::FromArgb(180, 195, 215)
$muted = [System.Drawing.Color]::FromArgb(100, 116, 139)
$amber = [System.Drawing.Color]::FromArgb(255, 179, 0)
$borderCol = [System.Drawing.Color]::FromArgb(30, 45, 68)

function Draw-RoundedRect($g, $brush, $pen, $x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($x, $y, $r*2, $r*2, 180, 90)
    $path.AddArc($x + $w - $r*2, $y, $r*2, $r*2, 270, 90)
    $path.AddArc($x + $w - $r*2, $y + $h - $r*2, $r*2, $r*2, 0, 90)
    $path.AddArc($x, $y + $h - $r*2, $r*2, $r*2, 90, 90)
    $path.CloseFigure()
    if ($brush) { $g.FillPath($brush, $path) }
    if ($pen) { $g.DrawPath($pen, $path) }
    $path.Dispose()
}

# ==========================================
# SCREENSHOT 1: Phone Main Screen (1080x2400)
# ==========================================
$bmp1 = New-Object System.Drawing.Bitmap 1080, 2400, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g1 = [System.Drawing.Graphics]::FromImage($bmp1)
$g1.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g1.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g1.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

# Solid dark background
$brushBg = New-Object System.Drawing.SolidBrush $bgDark
$g1.FillRectangle($brushBg, 0, 0, 1080, 2400)

$fStatus = New-Object System.Drawing.Font('Segoe UI', [float]24, [System.Drawing.FontStyle]::Bold)
$fSmall = New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Regular)
$bWhite = New-Object System.Drawing.SolidBrush $white
$bSilver = New-Object System.Drawing.SolidBrush $silver
$bMuted = New-Object System.Drawing.SolidBrush $muted
$bCyan = New-Object System.Drawing.SolidBrush $cyan
$pCyan = New-Object System.Drawing.Pen $cyan, 2
$pBorder = New-Object System.Drawing.Pen $borderCol, 2

$g1.DrawString("9:41", $fStatus, $bWhite, 60, 45)
$g1.DrawString("100%  -  LTE", $fSmall, $bSilver, 860, 50)

# Top Bar
$fBrand = New-Object System.Drawing.Font('Segoe UI', [float]32, [System.Drawing.FontStyle]::Bold)
$fSub = New-Object System.Drawing.Font('Segoe UI', [float]15, [System.Drawing.FontStyle]::Bold)
$g1.FillEllipse($bCyan, 60, 135, 18, 18)
$g1.DrawString("ANCHOR", $fBrand, $bWhite, 90, 120)
$g1.DrawString("ROCK-SOLID TIME FOR SERIOUS PLAY", $fSub, $bCyan, 60, 180)

# Speaker output pill
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pCyan 680 125 180 55 12
$fPill = New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("Speaker", $fPill, $bCyan, 730, 137)

# Settings button
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 890 125 120 55 12
$g1.DrawString("...", $fPill, $bSilver, 935, 137)

# Setlist bar
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 60 230 960 90 16
$g1.FillRectangle((New-Object System.Drawing.SolidBrush $cyanGlow), 80, 250, 100, 45)
$g1.DrawRectangle($pCyan, 80, 250, 100, 45)
$fTag = New-Object System.Drawing.Font('Segoe UI', [float]14, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("SETLIST", $fTag, $bCyan, 92, 262)

$fSong = New-Object System.Drawing.Font('Segoe UI', [float]24, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("Friday Gig - Opener", $fSong, $bWhite, 205, 254)
$g1.DrawString("<   >", $fSong, $bCyan, 880, 254)

# Pendulum Visual Track
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 60 360 960 260 20
$pTrack = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(40, 60, 90)), 3
$g1.DrawArc($pTrack, 200, 320, 680, 280, 40, 100)

# Pendulum Arm & Weight
$pArm = New-Object System.Drawing.Pen $cyan, 5
$g1.DrawLine($pArm, 540, 600, 680, 420)
$g1.FillEllipse((New-Object System.Drawing.SolidBrush $amber), 660, 405, 42, 42)
$g1.FillEllipse($bWhite, 532, 592, 16, 16)

# Beat Bar (4 beats)
$beatX = 140
for ($i = 0; $i -lt 4; $i++) {
    $curX = $beatX + ($i * 210)
    if ($i -eq 0) {
        $g1.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(60, 0, 229, 255))), $curX-12, 658, 104, 104)
        $g1.FillEllipse($bCyan, $curX, 670, 80, 80)
        $fBeatNum = New-Object System.Drawing.Font('Segoe UI', [float]30, [System.Drawing.FontStyle]::Bold)
        $g1.DrawString("1", $fBeatNum, (New-Object System.Drawing.SolidBrush $bgDark), $curX+28, 682)
    } else {
        $g1.FillEllipse((New-Object System.Drawing.SolidBrush $surfaceBg), $curX, 670, 80, 80)
        $g1.DrawEllipse($pBorder, $curX, 670, 80, 80)
        $fBeatNum = New-Object System.Drawing.Font('Segoe UI', [float]30, [System.Drawing.FontStyle]::Bold)
        $g1.DrawString(($i+1).ToString(), $fBeatNum, $bSilver, $curX+28, 682)
    }
}

# Main Tempo Display
$fBpmHuge = New-Object System.Drawing.Font('Segoe UI', [float]130, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("120", $fBpmHuge, $bWhite, 360, 800)

$fMarking = New-Object System.Drawing.Font('Segoe UI', [float]26, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("BPM  -  MODERATO", $fMarking, $bCyan, 375, 1040)

# Tempo Step Buttons
$steps = @("-5", "-1", "+1", "+5")
for ($i = 0; $i -lt 4; $i++) {
    $bx = 120 + ($i * 220)
    Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder $bx 1130 180 85 16
    $fBtn = New-Object System.Drawing.Font('Segoe UI', [float]30, [System.Drawing.FontStyle]::Bold)
    $g1.DrawString($steps[$i], $fBtn, $bCyan, $bx+55, 1145)
}

# Half / Double / Tap Tempo
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 120 1260 250 85 16
$g1.DrawString("1/2 Time", (New-Object System.Drawing.Font('Segoe UI', [float]22, [System.Drawing.FontStyle]::Bold)), $bSilver, 180, 1285)

Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cyanGlow) $pCyan 415 1260 250 85 16
$g1.DrawString("TAP TEMPO", (New-Object System.Drawing.Font('Segoe UI', [float]20, [System.Drawing.FontStyle]::Bold)), $bCyan, 455, 1287)

Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 710 1260 250 85 16
$g1.DrawString("2x Time", (New-Object System.Drawing.Font('Segoe UI', [float]22, [System.Drawing.FontStyle]::Bold)), $bSilver, 770, 1285)

# Subdivisions & Time Sig
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 60 1400 960 160 20
$fLabel = New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("METER & SUBDIVISION", $fLabel, $bMuted, 90, 1425)

$fMeter = New-Object System.Drawing.Font('Segoe UI', [float]38, [System.Drawing.FontStyle]::Bold)
$g1.DrawString("4 / 4", $fMeter, $bWhite, 90, 1465)

$subdivs = @("Quarter", "8th", "Triplet", "16th")
for ($i = 0; $i -lt 4; $i++) {
    $sx = 340 + ($i * 160)
    $isSel = ($i -eq 0)
    $sBrush = if ($isSel) { New-Object System.Drawing.SolidBrush $cyanGlow } else { New-Object System.Drawing.SolidBrush $surfaceBg }
    $sPen = if ($isSel) { $pCyan } else { $pBorder }
    $sTextBrush = if ($isSel) { $bCyan } else { $bSilver }
    Draw-RoundedRect $g1 $sBrush $sPen $sx 1450 140 70 12
    $g1.DrawString($subdivs[$i], (New-Object System.Drawing.Font('Segoe UI', [float]16, [System.Drawing.FontStyle]::Bold)), $sTextBrush, $sx+25, 1472)
}

# Play Button
$g1.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 0, 229, 255))), 390, 1680, 300, 300)
$g1.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(90, 0, 229, 255))), 430, 1720, 220, 220)
$g1.FillEllipse($bCyan, 460, 1750, 160, 160)

$playPoints = @(
    (New-Object System.Drawing.Point 530, 1800),
    (New-Object System.Drawing.Point 570, 1830),
    (New-Object System.Drawing.Point 530, 1860)
)
$g1.FillPolygon((New-Object System.Drawing.SolidBrush $bgDark), $playPoints)

# Bottom Feature Pills
Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 140 2070 360 80 16
$g1.DrawString("Visual Flash: ON", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bCyan, 205, 2095)

Draw-RoundedRect $g1 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 580 2070 360 80 16
$g1.DrawString("Haptic Pulse: ON", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bCyan, 645, 2095)

# Footnote
$g1.DrawString("100% OFFLINE   -   NO ADS   -   ZERO TRACKING", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bMuted, 280, 2260)

$bmp1.Save("$phoneDir/1_main_metronome.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bmp1.Dispose()
Write-Host "Created phone screenshot 1: 1_main_metronome.png"

# ==========================================
# SCREENSHOT 2: Stage Setlists (1080x2400)
# ==========================================
$bmp2 = New-Object System.Drawing.Bitmap 1080, 2400, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g2 = [System.Drawing.Graphics]::FromImage($bmp2)
$g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g2.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

$g2.FillRectangle((New-Object System.Drawing.SolidBrush $bgDark), 0, 0, 1080, 2400)

$g2.DrawString("9:41", $fStatus, $bWhite, 60, 45)
$g2.DrawString("100%  -  LTE", $fSmall, $bSilver, 860, 50)

$fHeader = New-Object System.Drawing.Font('Segoe UI', [float]38, [System.Drawing.FontStyle]::Bold)
$g2.DrawString("Stage Setlists & Repertoire", $fHeader, $bWhite, 60, 130)
$g2.DrawString("Organize songs with tempo, meter, and custom accents for live gigs.", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Regular)), $bSilver, 60, 195)

# Playlists Tabs
Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $cyanGlow) $pCyan 60 270 300 70 14
$g2.DrawString("Friday Gig (8 songs)", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bCyan, 100, 292)

Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 380 270 280 70 14
$g2.DrawString("Acoustic Rehearsal", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bSilver, 410, 292)

Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 680 270 220 70 14
$g2.DrawString("Jazz Trio", (New-Object System.Drawing.Font('Segoe UI', [float]18, [System.Drawing.FontStyle]::Bold)), $bSilver, 730, 292)

$songs = @(
    @{ title="1. Opener (Main Groove)"; bpm="120 BPM"; meter="4/4 Time"; active=$true },
    @{ title="2. Neon Skyline"; bpm="142 BPM"; meter="4/4 Time"; active=$false },
    @{ title="3. 7/8 Rush & Polyrhythm"; bpm="175 BPM"; meter="7/8 Time"; active=$false },
    @{ title="4. Midnight Ballad"; bpm="68 BPM"; meter="6/8 Time"; active=$false },
    @{ title="5. Funk Odyssey"; bpm="116 BPM"; meter="4/4 Time"; active=$false },
    @{ title="6. Heavy Encore"; bpm="158 BPM"; meter="4/4 Time"; active=$false }
)

$sy = 380
foreach ($s in $songs) {
    $cardBrush = if ($s.active) { New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(18, 30, 48)) } else { New-Object System.Drawing.SolidBrush $cardBg }
    $cardPen = if ($s.active) { $pCyan } else { $pBorder }
    Draw-RoundedRect $g2 $cardBrush $cardPen 60 $sy 960 170 18
    
    $fTitle = New-Object System.Drawing.Font('Segoe UI', [float]26, [System.Drawing.FontStyle]::Bold)
    $titleBrush = if ($s.active) { $bCyan } else { $bWhite }
    $g2.DrawString($s.title, $fTitle, $titleBrush, 95, ($sy + 30))
    
    $fMeta = New-Object System.Drawing.Font('Segoe UI', [float]20, [System.Drawing.FontStyle]::Bold)
    $g2.DrawString($s.bpm + "   -   " + $s.meter, $fMeta, $bSilver, 95, ($sy + 85))
    
    if ($s.active) {
        Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $cyan) $null 820 ($sy + 45) 160 70 14
        $g2.DrawString("PLAYING", (New-Object System.Drawing.Font('Segoe UI', [float]16, [System.Drawing.FontStyle]::Bold)), (New-Object System.Drawing.SolidBrush $bgDark), 855, ($sy + 68))
    } else {
        Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $surfaceBg) $pBorder 840 ($sy + 45) 140 70 14
        $g2.DrawString("LOAD", (New-Object System.Drawing.Font('Segoe UI', [float]16, [System.Drawing.FontStyle]::Bold)), $bCyan, 880, ($sy + 68))
    }
    
    $sy += 210
}

# Bottom Action Button
Draw-RoundedRect $g2 (New-Object System.Drawing.SolidBrush $cyan) $null 120 1800 840 100 20
$g2.DrawString("+ Add New Song to Repertoire", (New-Object System.Drawing.Font('Segoe UI', [float]26, [System.Drawing.FontStyle]::Bold)), (New-Object System.Drawing.SolidBrush $bgDark), 280, 1830)

$bmp2.Save("$phoneDir/2_stage_setlists.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bmp2.Dispose()
Write-Host "Created phone screenshot 2: 2_stage_setlists.png"

# ==========================================
# SCREENSHOT 3: Tablet Studio Split (1920x1200)
# ==========================================
$bmp3 = New-Object System.Drawing.Bitmap 1920, 1200, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g3 = [System.Drawing.Graphics]::FromImage($bmp3)
$g3.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g3.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

$g3.FillRectangle((New-Object System.Drawing.SolidBrush $bgDark), 0, 0, 1920, 1200)

$g3.FillEllipse($bCyan, 60, 45, 20, 20)
$g3.DrawString("ANCHOR", $fBrand, $bWhite, 95, 30)
$g3.DrawString("TWO-PANE STUDIO LAYOUT FOR TABLETS & WIDE DISPLAYS", $fSub, $bCyan, 280, 48)

# Left Pane
Draw-RoundedRect $g3 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 60 110 780 1020 20
$g3.DrawString("STAGE SETLIST: Friday Gig", $fMarking, $bCyan, 95, 145)

$tabY = 220
foreach ($s in $songs[0..3]) {
    $cardBrush = if ($s.active) { New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(20, 36, 56)) } else { New-Object System.Drawing.SolidBrush $surfaceBg }
    $cardPen = if ($s.active) { $pCyan } else { $pBorder }
    Draw-RoundedRect $g3 $cardBrush $cardPen 95 $tabY 710 130 14
    $g3.DrawString($s.title, (New-Object System.Drawing.Font('Segoe UI', [float]20, [System.Drawing.FontStyle]::Bold)), $bWhite, 120, $tabY + 25)
    $g3.DrawString($s.bpm + " - " + $s.meter, (New-Object System.Drawing.Font('Segoe UI', [float]16, [System.Drawing.FontStyle]::Regular)), $bSilver, 120, $tabY + 70)
    $tabY += 160
}

# Right Pane
Draw-RoundedRect $g3 (New-Object System.Drawing.SolidBrush $cardBg) $pBorder 880 110 980 1020 20
$g3.DrawString("120", (New-Object System.Drawing.Font('Segoe UI', [float]110, [System.Drawing.FontStyle]::Bold)), $bWhite, 1220, 160)
$g3.DrawString("BPM  -  MODERATO", $fMarking, $bCyan, 1230, 360)

# Beat Indicators
$bxTablet = 1020
for ($i = 0; $i -lt 4; $i++) {
    $curX = $bxTablet + ($i * 180)
    $bColor = if ($i -eq 0) { $cyan } else { $surfaceBg }
    $g3.FillEllipse((New-Object System.Drawing.SolidBrush $bColor), $curX, 440, 90, 90)
    $g3.DrawEllipse($pCyan, $curX, 440, 90, 90)
    $numBrush = if ($i -eq 0) { New-Object System.Drawing.SolidBrush $bgDark } else { $bWhite }
    $g3.DrawString(($i+1).ToString(), (New-Object System.Drawing.Font('Segoe UI', [float]30, [System.Drawing.FontStyle]::Bold)), $numBrush, $curX+32, 455)
}

# Right Controls
Draw-RoundedRect $g3 (New-Object System.Drawing.SolidBrush $surfaceBg) $pBorder 1020 590 700 90 16
$g3.DrawString("-5      -1      +1      +5      |      TAP TEMPO", (New-Object System.Drawing.Font('Segoe UI', [float]22, [System.Drawing.FontStyle]::Bold)), $bCyan, 1080, 618)

# Big Play Button
$g3.FillEllipse((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(60, 0, 229, 255))), 1260, 750, 220, 220)
$g3.FillEllipse($bCyan, 1290, 780, 160, 160)
$playPointsTablet = @(
    (New-Object System.Drawing.Point 1355, 830),
    (New-Object System.Drawing.Point 1405, 860),
    (New-Object System.Drawing.Point 1355, 890)
)
$g3.FillPolygon((New-Object System.Drawing.SolidBrush $bgDark), $playPointsTablet)

$bmp3.Save("$sevenDir/1_tablet_studio_split.png", [System.Drawing.Imaging.ImageFormat]::Png)
$bmp3.Dispose()
Write-Host "Created tablet screenshot: 1_tablet_studio_split.png"
