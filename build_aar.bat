@echo off
REM ============================================
REM 从私有仓库构建闭源模块 AAR 并复制到 libs/
REM 使用方法：在私有仓库根目录执行此脚本
REM ============================================

set PRIVATE_REPO=..\wuminpy
set PUBLIC_LIBS=libs

echo [1/7] Building :core ...
cd /d "%PRIVATE_REPO%"
call gradlew :core:assembleRelease
copy /Y core\build\outputs\aar\core-release.aar "%PUBLIC_LIBS%\"

echo [2/7] Building :python ...
call gradlew :python:assembleRelease
copy /Y python\build\outputs\aar\python-release.aar "%PUBLIC_LIBS%\"

echo [3/7] Building :merminal ...
call gradlew :merminal:assembleRelease
copy /Y merminal\build\outputs\aar\merminal-release.aar "%PUBLIC_LIBS%\"

echo [4/7] Building :git ...
call gradlew :git:assembleRelease
copy /Y git\build\outputs\aar\git-release.aar "%PUBLIC_LIBS%\"

echo [5/7] Building :codeeditor ...
call gradlew :codeeditor:assembleRelease
copy /Y codeeditor\build\outputs\aar\codeeditor-release.aar "%PUBLIC_LIBS%\"

echo [6/7] Building :debug ...
call gradlew :debug:assembleRelease
copy /Y debug\build\outputs\aar\debug-release.aar "%PUBLIC_LIBS%\"

echo [7/7] Building :ai ...
call gradlew :ai:assembleRelease
copy /Y ai\build\outputs\aar\ai-release.aar "%PUBLIC_LIBS%\"

echo ============================================
echo Done! AAR files copied to libs/
echo Public repo is ready to build.
echo ============================================
