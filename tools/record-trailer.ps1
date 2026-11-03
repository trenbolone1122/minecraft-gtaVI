<#
.SYNOPSIS
  Records the 60 s Leonida trailer on a Windows PC: renders each shot frame by frame (1920x1080, 60 fps,
  however slow the GPU is), fetches and cuts the music, mixes the mod's own SFX and assembles the trailer
  with hard cuts on the timeline in shots.json ("trailer60").

.DESCRIPTION
  Needs java (JDK 25; JAVA_HOME is used if set), ffmpeg, ffprobe and yt-dlp on PATH. Nothing is downloaded
  except the music (yt-dlp, only when -Music is not given). Runs gradlew.bat runClientGameTest with the fx test
  and shaders, Distant Horizons off and the building cache off.

.EXAMPLE
  .\tools\record-trailer.ps1
  .\tools\record-trailer.ps1 -RenderDistance 16
  .\tools\record-trailer.ps1 -Shots 4,12,19 -NoAssemble
  .\tools\record-trailer.ps1 -SkipRender -Bpm 128
  .\tools\record-trailer.ps1 -SkipRender -Music C:\music\my-60s-cut.wav
#>
param(
	# Shots to render: timeline numbers 1-19 (T01..T19 in shots.json) or shot names. Default: all 19.
	[string[]]$Shots = @(),
	# Chunk render distance for every shot. 32 matches the reference; 16-24 is lighter on 4 GB cards.
	[int]$RenderDistance = 32,
	# Music override: a ready audio file, placed from 0 s as given (no download).
	[string]$Music = "",
	# Otherwise the music is fetched with yt-dlp and cut: 60 s from -MusicStart, 1.0 s fade in, 2.5 s fade out.
	[string]$MusicUrl = "https://www.youtube.com/watch?v=HJcZhkNOsbI",
	[double]$MusicStart = 14.1,
	# Optional beat snap: cuts move to the nearest beat of a -Bpm grid that has a beat at -DropAt.
	[double]$Bpm = 0,
	[double]$DropAt = 12.5,
	# Where per-shot MP4s, stills and the trailer go.
	[string]$OutDir = "",
	# Skip rendering and only assemble clips already in -OutDir.
	[switch]$SkipRender,
	# Render only, no music or trailer assembly.
	[switch]$NoAssemble
)

$ErrorActionPreference = "Stop"
$Repo = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $Repo "trailer-out" }
$OutDir = [IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$inv = [Globalization.CultureInfo]::InvariantCulture
function F([double]$v) { return $v.ToString("0.######", $inv) }
function Fail([string]$msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

function Find-Tool([string]$name, [string]$hint) {
	$c = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
	if (-not $c) { Fail "$name not found on PATH ($hint)." }
	return $c.Source
}

# ---------------------------------------------------------------- tools (fail early, one line each)
$needRender = -not $SkipRender
$needAssemble = -not $NoAssemble
if ($needRender) {
	if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
		$java = Join-Path $env:JAVA_HOME "bin\java.exe"
	} else {
		$java = Find-Tool "java" "install JDK 25 or set JAVA_HOME"
		$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-Item $java).FullName)
	}
}
$ffmpeg = Find-Tool "ffmpeg" "needed to encode the clips"
$ffprobe = Find-Tool "ffprobe" "comes with ffmpeg"
if ($needAssemble -and -not $Music) { $ytdlp = Find-Tool "yt-dlp" "needed to fetch the music, or pass -Music <file>" }
if ($Music -and -not (Test-Path $Music)) { Fail "Music file not found: $Music" }

# ---------------------------------------------------------------- timeline from shots.json
$shotFile = Join-Path $Repo "src\gametest\java\dev\leonida\test\fx\cine\shots.json"
$json = Get-Content -Raw -Path $shotFile | ConvertFrom-Json
$tl = $json.trailer60
$all = @()
foreach ($n in $tl.shots) {
	$hit = $json.shots | Where-Object { $_.name -eq $n } | Select-Object -First 1
	if (-not $hit) { Fail "trailer60 shot '$n' is missing from shots.json" }
	$all += $hit
}
$cuts = @($tl.cuts | ForEach-Object { [double]$_ })
if ($cuts.Count -ne $all.Count + 1) { Fail "trailer60 needs one more cut than shots ($($cuts.Count) cuts, $($all.Count) shots)" }

$sel = @()
if ($Shots.Count -eq 0) { $sel = $all } else {
	foreach ($s in ($Shots -join ",").Split(",", [StringSplitOptions]::RemoveEmptyEntries)) {
		$s = $s.Trim()
		$hit = $null
		$num = 0
		if ([int]::TryParse($s, [ref]$num)) { if ($num -ge 1 -and $num -le $all.Count) { $hit = $all[$num - 1] } }
		else { $hit = $json.shots | Where-Object { $_.name -eq $s } | Select-Object -First 1 }
		if (-not $hit) { Fail "Shot '$s' not found (use 1-$($all.Count) or a shot name)" }
		$sel += $hit
	}
}

Write-Host "ffmpeg: $ffmpeg"
Write-Host ("Shots:  " + (($sel | ForEach-Object { $_.outName }) -join " "))

# ---------------------------------------------------------------- render
if ($needRender) {
	Write-Host "JDK:    $env:JAVA_HOME"
	# Read by CineCapture (system property leonida.cine.* or env LEONIDA_CINE_*); shots.json itself is not edited.
	$env:LEONIDA_CINE_RUN = (($sel | ForEach-Object { $_.name }) -join ",")
	$env:LEONIDA_CINE_OUT = $OutDir
	$env:LEONIDA_CINE_FFMPEG = $ffmpeg
	$env:LEONIDA_CINE_RD = "$RenderDistance"
	$env:LEONIDA_CINE_DH = "false"
	$env:LEONIDA_CINE_CACHE = "false"
	# A world left over from a previous run would be reused; start clean so every shot sees fresh chunks.
	$reuse = Join-Path $Repo "build\run\clientGameTest\saves\cine-reuse"
	if (Test-Path $reuse) { Remove-Item -Recurse -Force $reuse }
	Write-Host "Rendering (the game window may look slow or frozen; frames are captured offscreen at 1920x1080) ..."
	Push-Location $Repo
	try {
		& .\gradlew.bat runClientGameTest "-PleonidaTest=fx" "-PwithShaders" --console=plain
		if ($LASTEXITCODE -ne 0) { Write-Warning "gradlew exited with $LASTEXITCODE; assembling whatever clips exist." }
	} finally {
		Pop-Location
	}
}
if ($NoAssemble) { Write-Host "Clips in $OutDir"; exit 0 }

# ---------------------------------------------------------------- cut times (optional beat snap)
if ($Bpm -gt 0) {
	$beat = 60.0 / $Bpm
	for ($i = 1; $i -lt $cuts.Count - 1; $i++) {
		$c = $DropAt + [Math]::Round(($cuts[$i] - $DropAt) / $beat) * $beat
		if ($c -le $cuts[$i - 1] + 0.5) { $c = $cuts[$i] }
		$cuts[$i] = $c
	}
	Write-Host ("Cuts snapped to {0} BPM: {1}" -f (F $Bpm), (($cuts | ForEach-Object { F $_ }) -join " "))
}
$Total = $cuts[$cuts.Count - 1]
$margin = 0.25 # each clip renders its slot + 0.5 s; the slot is taken from 0.25 s in

# ---------------------------------------------------------------- music
$musicCut = Join-Path $OutDir "music-60s.wav"
if ($Music) {
	& $ffmpeg -y -loglevel error -i $Music -t (F $Total) -ar 48000 -ac 2 $musicCut
} else {
	$src = Join-Path $OutDir "music-src"
	Get-ChildItem -Path $OutDir -Filter "music-src.*" -ErrorAction SilentlyContinue | Remove-Item -Force
	Write-Host "Fetching music: $MusicUrl"
	& $ytdlp -f bestaudio -x --audio-format wav --no-playlist -o "$src.%(ext)s" $MusicUrl
	if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$src.wav")) { Fail "yt-dlp could not fetch $MusicUrl (pass -Music <file> instead)" }
	& $ffmpeg -y -loglevel error -ss (F $MusicStart) -t (F $Total) -i "$src.wav" -af ("afade=t=in:st=0:d=1.0,afade=t=out:st={0}:d=2.5" -f (F ($Total - 2.5))) -ar 48000 -ac 2 $musicCut
}
if ($LASTEXITCODE -ne 0) { Fail "ffmpeg could not prepare the music" }

# ---------------------------------------------------------------- SFX layer (the mod's own sounds)
$sounds = Join-Path $Repo "src\main\resources\assets\leonida\sounds"
$sfx = @()
foreach ($e in $tl.sfx) {
	$f = Join-Path $sounds ($e.file -replace "/", "\")
	if (-not (Test-Path $f)) { Write-Warning "SFX missing: $f"; continue }
	$i = [int]$e.shot - 1
	$dur = [double]::Parse((& $ffprobe -v error -show_entries format=duration -of csv=p=0 $f).Trim(), $inv)
	if ($e.PSObject.Properties.Name -contains "center") { $at = $cuts[$i] + [double]$e.center - $margin - $dur / 2 }
	else { $at = $cuts[$i] + [double]$e.at - $margin }
	$sfx += [pscustomobject]@{ File = $f; At = [Math]::Max(0.0, $at); Dur = $dur; Duck = ($e.PSObject.Properties.Name -contains "center") }
}

# ---------------------------------------------------------------- video: hard cuts, fade in from black, fade out to black
$in = @("-y", "-loglevel", "error")
$filter = @()
$labels = ""
$idx = 0
for ($i = 0; $i -lt $all.Count; $i++) {
	$len = $cuts[$i + 1] - $cuts[$i]
	$f = Join-Path $OutDir "$($all[$i].outName).mp4"
	if (Test-Path $f) {
		$in += @("-i", $f)
		$filter += "[$($idx):v]trim=start=$(F $margin):duration=$(F $len),setpts=PTS-STARTPTS,fps=60,scale=1920:1080,setsar=1,format=yuv420p,tpad=stop_mode=clone:stop_duration=1,trim=duration=$(F $len)[v$i]"
	} else {
		Write-Warning "Missing clip $f (black in its slot)"
		$in += @("-f", "lavfi", "-i", "color=c=black:s=1920x1080:r=60:d=$(F $len)")
		$filter += "[$($idx):v]setsar=1,format=yuv420p[v$i]"
	}
	$labels += "[v$i]"
	$idx++
}
$filter += "$($labels)concat=n=$($all.Count):v=1:a=0,fade=t=in:st=0:d=1.0,fade=t=out:st=$(F ($Total - 1.5)):d=1.5[vout]"

# ---------------------------------------------------------------- audio: music (ducked under the woosh) + SFX at -6 dB
$in += @("-i", $musicCut)
$m = $idx
$idx++
$duck = "1"
foreach ($s in ($sfx | Where-Object { $_.Duck })) { $duck = "if(between(t,$(F $s.At),$(F ($s.At + $s.Dur))),0.63,$duck)" }
$filter += "[$($m):a]volume='$duck':eval=frame[mus]"
$mix = "[mus]"
$k = 0
foreach ($s in $sfx) {
	$in += @("-i", $s.File)
	$ms = [int][Math]::Round($s.At * 1000)
	$filter += "[$($idx):a]aresample=48000,aformat=channel_layouts=stereo,volume=0.5,adelay=$($ms)|$($ms)[s$k]"
	$mix += "[s$k]"
	$idx++
	$k++
}
$filter += "$($mix)amix=inputs=$($k + 1):duration=first:dropout_transition=0:normalize=0,atrim=duration=$(F $Total)[aout]"

$trailer = Join-Path $OutDir "leonida-trailer-60s.mp4"
$args2 = $in + @("-filter_complex", ($filter -join ";"), "-map", "[vout]", "-map", "[aout]",
	"-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-r", "60",
	"-c:a", "aac", "-b:a", "256k", "-t", (F $Total), "-movflags", "+faststart", $trailer)
Write-Host ("Assembling {0} shots, {1} s, hard cuts ..." -f $all.Count, (F $Total))
& $ffmpeg @args2
if ($LASTEXITCODE -ne 0) { Fail "ffmpeg assembly failed ($LASTEXITCODE)" }
$len = (& $ffprobe -v error -show_entries format=duration -of csv=p=0 $trailer).Trim()
Write-Host "Trailer: $trailer ($len s)"
