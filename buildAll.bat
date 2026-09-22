@echo off
rem Build both distributable jars in one go (Windows).
rem   - desktop: Jar-in-Jar LWJGL + bundled natives, version without suffix
rem   - android: no embedded LWJGL / no natives (FCL ships them), version with -android suffix

echo Building desktop jar (Jar-in-Jar LWJGL + bundled natives)...
call gradlew.bat build -Pvulkanmod_android=false
if errorlevel 1 exit /b 1

echo Building Android/FCL jar (no embedded LWJGL, no natives)...
call gradlew.bat build -Pvulkanmod_android=true
if errorlevel 1 exit /b 1

echo Done. Distributable jars:
dir /b build\libs\VulkanMod_1.20.1-*.jar
