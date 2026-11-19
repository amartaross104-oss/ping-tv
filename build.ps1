$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$sdk  = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\android-sdk" }
$bt   = "$sdk\build-tools\34.0.0"
$jar  = "$sdk\platforms\android-34\android.jar"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Microsoft' -Filter 'jdk-17*' -Directory | Select-Object -First 1).FullName }
$jbin = "$env:JAVA_HOME\bin"
$out  = "$root\build"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes","$out\dex","$root\app\res\drawable" | Out-Null

# launcher banner (320x180)
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 320,180
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAlias'
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush ([System.Drawing.Point]::new(0,0)),([System.Drawing.Point]::new(320,180)),([System.Drawing.Color]::FromArgb(7,19,22)),([System.Drawing.Color]::FromArgb(22,64,63))
$g.FillRectangle($bg,0,0,320,180)
$teal = [System.Drawing.Color]::FromArgb(79,199,178)
$pen = New-Object System.Drawing.Pen $teal,6
$pen.StartCap='Round'; $pen.EndCap='Round'; $pen.LineJoin='Round'
$g.DrawLines($pen,@([System.Drawing.Point]::new(30,95),[System.Drawing.Point]::new(60,95),[System.Drawing.Point]::new(75,55),[System.Drawing.Point]::new(95,125),[System.Drawing.Point]::new(112,80),[System.Drawing.Point]::new(125,95),[System.Drawing.Point]::new(150,95)))
$g.DrawString('Ping Internet',(New-Object System.Drawing.Font 'Segoe UI Semibold',24),(New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(234,242,240))),26,128)
$bmp.Save("$root\app\res\drawable\banner.png",[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

& "$bt\aapt2.exe" compile --dir "$root\app\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$root\app\AndroidManifest.xml" "$out\res.zip" --min-sdk-version 30 --target-sdk-version 34
& "$jbin\javac.exe" --release 8 -encoding UTF-8 -cp $jar -d "$out\classes" (Get-ChildItem "$root\app\src" -Recurse -Filter *.java | ForEach-Object FullName) 2>&1 | Where-Object { $_ -notmatch 'warning|Note:' }
$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$jbin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib $jar --output "$out\dex" @($classes)
Push-Location "$out\dex"; & "$jbin\jar.exe" uf "$out\base.apk" classes.dex; Pop-Location
& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"

$ks = "$root\debug.keystore"
if (-not (Test-Path $ks)) {
  & "$jbin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=PingTV' | Out-Null
}
& "$jbin\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$root\PingTV.apk" "$out\aligned.apk"
Get-Item "$root\PingTV.apk" | Select-Object Name,Length

