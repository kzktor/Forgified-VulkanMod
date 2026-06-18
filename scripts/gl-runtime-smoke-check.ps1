param(
    [string]$LogPath = "run/logs/latest.log"
)

if (-not (Test-Path -LiteralPath $LogPath)) {
    Write-Error "Log file not found: $LogPath"
    exit 2
}

$text = Get-Content -LiteralPath $LogPath -Raw

$checks = [ordered]@{
    provider = @("Installed emulated GL capabilities", "GLCapabilities", "OpenGL")
    state_query = @("glGet", "state_query")
    texture_image = @("texture_image", "glTex", "glCompressedTex")
    framebuffer_readback = @("framebuffer_readback", "glReadPixels", "glBlitFramebuffer")
    shader_conversion = @("shader_conversion", "fallback shader", "Failed to compile shader")
    draw_path = @("draw_path", "glDraw", "drawElements", "drawArrays")
}

Write-Output "GL contract summary for $LogPath"

foreach ($family in $checks.Keys) {
    $count = 0
    foreach ($pattern in $checks[$family]) {
        $count += ([regex]::Matches($text, [regex]::Escape($pattern))).Count
    }
    Write-Output ("{0}: {1}" -f $family, $count)
}

$fatalPatterns = @("Exception", "FATAL", "GL contract violation", "VK_ERROR_DEVICE_LOST")
$fatalCount = 0
foreach ($pattern in $fatalPatterns) {
    $fatalCount += ([regex]::Matches($text, [regex]::Escape($pattern))).Count
}

Write-Output ("runtime_smoke: fatal_patterns={0}" -f $fatalCount)

if ($fatalCount -gt 0) {
    exit 1
}

exit 0
