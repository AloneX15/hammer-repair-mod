# Convierte las capturas del escaparate (HammerRepairShowcaseClientGameTest) en las imágenes optimizadas de
# docs/images: recortes, JPEG para fotos, PNG para el icono y el martillo recortado. Ver docs/capturas.md.
# Uso: powershell -ExecutionPolicy Bypass -File tools/build-showcase-images.ps1 -Source <carpeta de capturas>
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$Destination = "",
    [int]$JpegQuality = 88
)
$ErrorActionPreference = "Stop"
# En PowerShell 5.1 $PSScriptRoot no está disponible en los valores por defecto de param()
if (-not $Destination) { $Destination = Join-Path $PSScriptRoot "..\docs\images" }
$Destination = [System.IO.Path]::GetFullPath($Destination)
Add-Type -AssemblyName System.Drawing

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class Showcase {
    static byte[] Read(Bitmap bmp, out BitmapData data) {
        data = bmp.LockBits(new Rectangle(0, 0, bmp.Width, bmp.Height), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        byte[] px = new byte[data.Stride * bmp.Height];
        Marshal.Copy(data.Scan0, px, 0, px.Length);
        return px;
    }

    static void Write(Bitmap bmp, BitmapData data, byte[] px) {
        Marshal.Copy(px, 0, data.Scan0, px.Length);
        bmp.UnlockBits(data);
    }

    // Quita el fondo de hormigón lima: verde claramente dominante sobre rojo y azul
    public static Bitmap ChromaKey(Bitmap src) {
        Bitmap bmp = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp)) g.DrawImageUnscaled(src, 0, 0);
        BitmapData data;
        byte[] px = Read(bmp, out data);
        for (int i = 0; i < px.Length; i += 4) {
            int b = px[i], g = px[i + 1], r = px[i + 2];
            if (g > 40 && g > r * 1.25 && g > b * 1.6) px[i + 3] = 0;
        }
        Write(bmp, data, px);
        return bmp;
    }

    // Caja que contiene los píxeles no transparentes
    public static Rectangle OpaqueBounds(Bitmap bmp) {
        BitmapData data;
        byte[] px = Read(bmp, out data);
        int minX = bmp.Width, minY = bmp.Height, maxX = -1, maxY = -1;
        for (int y = 0; y < bmp.Height; y++)
            for (int x = 0; x < bmp.Width; x++)
                if (px[y * data.Stride + x * 4 + 3] > 0) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                }
        bmp.UnlockBits(data);
        return Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }

    public static Bitmap Crop(Bitmap src, Rectangle area) {
        Bitmap bmp = new Bitmap(area.Width, area.Height, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp))
            g.DrawImage(src, new Rectangle(0, 0, area.Width, area.Height), area, GraphicsUnit.Pixel);
        return bmp;
    }

    public static Bitmap Resize(Bitmap src, int width, int height) {
        Bitmap bmp = new Bitmap(width, height, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp)) {
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.CompositingQuality = CompositingQuality.HighQuality;
            using (ImageAttributes wrap = new ImageAttributes()) {
                wrap.SetWrapMode(WrapMode.TileFlipXY); // sin halo en los bordes
                g.DrawImage(src, new Rectangle(0, 0, width, height), 0, 0, src.Width, src.Height, GraphicsUnit.Pixel, wrap);
            }
        }
        return bmp;
    }

    // Icono: degradado radial dorado, sombra suave y el martillo centrado ocupando ~80 %
    public static Bitmap Icon(Bitmap hammer, int size) {
        Bitmap bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp)) {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            using (GraphicsPath path = new GraphicsPath()) {
                path.AddEllipse(-size * 0.35f, -size * 0.35f, size * 1.7f, size * 1.7f);
                using (PathGradientBrush brush = new PathGradientBrush(path)) {
                    brush.CenterColor = Color.FromArgb(255, 250, 214, 120);
                    brush.SurroundColors = new[] { Color.FromArgb(255, 176, 96, 28) };
                    brush.CenterPoint = new PointF(size * 0.45f, size * 0.4f);
                    g.FillRectangle(brush, 0, 0, size, size);
                }
            }
            float scale = Math.Min(size * 0.8f / hammer.Width, size * 0.8f / hammer.Height);
            int w = (int) Math.Round(hammer.Width * scale), h = (int) Math.Round(hammer.Height * scale);
            int x = (size - w) / 2, y = (size - h) / 2;
            // Sombra: el martillo en negro translúcido, desplazado
            using (ImageAttributes shadow = new ImageAttributes()) {
                ColorMatrix m = new ColorMatrix(new float[][] {
                    new float[] {0, 0, 0, 0, 0}, new float[] {0, 0, 0, 0, 0}, new float[] {0, 0, 0, 0, 0},
                    new float[] {0, 0, 0, 0.35f, 0}, new float[] {0, 0, 0, 0, 1}});
                shadow.SetColorMatrix(m);
                int offset = size / 40;
                g.DrawImage(hammer, new Rectangle(x + offset, y + offset, w, h), 0, 0, hammer.Width, hammer.Height, GraphicsUnit.Pixel, shadow);
            }
            g.DrawImage(hammer, new Rectangle(x, y, w, h));
        }
        return bmp;
    }
}
"@

New-Item -ItemType Directory -Force $Destination | Out-Null
$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq "image/jpeg" }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, [long]$JpegQuality)

function Load([string]$name) { New-Object System.Drawing.Bitmap (Join-Path $Source "$name.png") }
function SaveJpeg($bmp, [string]$name) {
    # JPEG no tiene alfa: se aplana sobre negro (las capturas son opacas)
    $flat = New-Object System.Drawing.Bitmap $bmp.Width, $bmp.Height, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($flat); $g.DrawImageUnscaled($bmp, 0, 0); $g.Dispose()
    $flat.Save((Join-Path $Destination "$name.jpg"), $jpeg, $params); $flat.Dispose()
}
function SavePng($bmp, [string]$name) { $bmp.Save((Join-Path $Destination "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png) }
function Rect([int]$x, [int]$y, [int]$w, [int]$h) { New-Object System.Drawing.Rectangle $x, $y, $w, $h }

# Banner 3:1 tal cual (1920x640)
$img = Load "banner_raw"; SaveJpeg $img "banner"; $img.Dispose()

# Reparación cooperativa: recorte 16:9 centrado en los dos jugadores, sin reescalar
$img = Load "cooperative_repair_2"; $c = [Showcase]::Crop($img, (Rect 200 120 1600 900)); SaveJpeg $c "cooperative-repair"; $c.Dispose(); $img.Dispose()

# Reparación propia: captura completa a 1600x900
$img = Load "self_repair"; $r = [Showcase]::Resize($img, 1600, 900); SaveJpeg $r "self-repair"; $r.Dispose(); $img.Dispose()

# Receta y tooltip: recortes de la interfaz (escala 4 y 3), sin reescalar para que el texto siga nítido
$img = Load "crafting"; $c = [Showcase]::Crop($img, (Rect 590 190 740 700)); SaveJpeg $c "recipe"; $c.Dispose(); $img.Dispose()
$img = Load "crafting_tooltip"; $c = [Showcase]::Crop($img, (Rect 680 275 1210 530)); SaveJpeg $c "tooltip"; $c.Dispose(); $img.Dispose()

# Icono: martillo recortado (transparente) y sobre el degradado, a 512 y 256
$img = Load "icon_raw"
$keyed = [Showcase]::ChromaKey($img)
$bounds = [Showcase]::OpaqueBounds($keyed)
$hammer = [Showcase]::Crop($keyed, $bounds)
SavePng $hammer "hammer"
foreach ($size in 512, 256) { $icon = [Showcase]::Icon($hammer, $size); SavePng $icon "icon-$size"; $icon.Dispose() }
$hammer.Dispose(); $keyed.Dispose(); $img.Dispose()

Get-ChildItem $Destination | Select-Object Name, @{ n = "KB"; e = { [math]::Round($_.Length / 1KB) } } | Format-Table -AutoSize
