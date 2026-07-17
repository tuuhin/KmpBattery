@echo off
setlocal enabledelayedexpansion

:: Default values
set "SOURCE_FILE="
set "OUTPUT_DIR=appx"
set "BG_COLOR=none"
set "ROUND_RADIUS="
set "GENERATE_SVG_OUT=0"

:: Parse arguments
:parse_args
if "%~1"=="" goto validate_args
if "%~1"=="-c" (
    set "BG_COLOR=%~2"
    shift & shift
    goto parse_args
)
if "%~1"=="--color" (
    set "BG_COLOR=%~2"
    shift & shift
    goto parse_args
)
if "%~1"=="-r" (
    if "%~2"=="" (
        set "ROUND_RADIUS=24"
        shift
    ) else (
        echo %~2 | findstr /R "^[0-9]" >nul
        if errorlevel 1 (
            set "ROUND_RADIUS=24"
            shift
        ) else (
            set "ROUND_RADIUS=%~2"
            shift & shift
        )
    )
    goto parse_args
)
if "%~1"=="--rounded" (
    if "%~2"=="" (
        set "ROUND_RADIUS=24"
        shift
    ) else (
        echo %~2 | findstr /R "^[0-9]" >nul
        if errorlevel 1 (
            set "ROUND_RADIUS=24"
            shift
        ) else (
            set "ROUND_RADIUS=%~2"
            shift & shift
        )
    )
    goto parse_args
)
if "%~1"=="-s" (
    set "GENERATE_SVG_OUT=1"
    shift
    goto parse_args
)
if "%~1"=="--svg" (
    set "GENERATE_SVG_OUT=1"
    shift
    goto parse_args
)

:: Catch positional files and folders
if "%SOURCE_FILE%"=="" (
    set "SOURCE_FILE=%~1"
    shift
    goto parse_args
)
if "%OUTPUT_DIR%"=="appx" (
    set "OUTPUT_DIR=%~1"
    shift
    goto parse_args
)
shift
goto parse_args

:validate_args
if "%SOURCE_FILE%"=="" (
    echo Usage: %~nx0 ^<icon.png^|icon.svg^> [output-directory] [options]
    echo Options:
    echo   -c, --color ^<color^>        Background color ^(e.g. "#C4F18C", blue, none^)
    echo   -r, --rounded [radius]     Clip and round corners. Defaults to 24.
    echo   -s, --svg                  Generate a processed output SVG along with the ICO
    exit /b 1
)

if not exist "%SOURCE_FILE%" (
    echo Error: File not found: %SOURCE_FILE%
    exit /b 1
)

:: Check extension
for %%I in ("%SOURCE_FILE%") do set "EXT=%%~xI"
set "EXT_LOWER=%EXT:.=%"
if /I not "%EXT_LOWER%"=="png" if /I not "%EXT_LOWER%"=="svg" (
    echo Error: Input must be a .png or .svg file
    exit /b 1
)

:: Check for ImageMagick
where magick >nul 2>nul
if errorlevel 1 (
    echo Error: ImageMagick ^(magick^) not found in PATH
    exit /b 1
)

:: Setup Directory Structures
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
for %%I in ("%OUTPUT_DIR%") do set "ICO_OUTPUT_DIR=%%~dpI"
for %%I in ("%SOURCE_FILE%") do set "ICO_BASE_NAME=%%~nI"

:: Strip trailing backslash from parent directory
if "%ICO_OUTPUT_DIR:~-1%"=="\" set "ICO_OUTPUT_DIR=%ICO_OUTPUT_DIR:~0,-1%"

:: -------------------------------------------------------------------------
:: Step 1: Generate or Prepare the Master PNG
:: -------------------------------------------------------------------------
set "MASTER_PNG=%TEMP%\master_icon_%RANDOM%.png"

if /I "%EXT_LOWER%"=="svg" (
    echo Converting SVG to a crisp intermediate high-res Master PNG...
    magick -density 300 -background "%BG_COLOR%" "%SOURCE_FILE%" -layers Flatten -resize 4096x4096 "%MASTER_PNG%"
) else (
    if not "%BG_COLOR%"=="none" (
        echo Applying background color to source PNG...
        magick "%SOURCE_FILE%" -background "%BG_COLOR%" -layers Flatten "%MASTER_PNG%"
    ) else (
        set "MASTER_PNG=%SOURCE_FILE%"
    )
)

:: -------------------------------------------------------------------------
:: Step 2: Asset Generation
:: -------------------------------------------------------------------------
echo Background color set to: %BG_COLOR%
if not "%ROUND_RADIUS%"=="" (
    echo Clip mode enabled ^(Radius: %ROUND_RADIUS%px^)
) else (
    echo Clip mode disabled ^(Square boundaries^)
)
echo Generating AppX/MSIX assets...

call :generate_logo 44  44  "Square44x44Logo"
call :generate_logo 150 150 "Square150x150Logo"
call :generate_logo 50  50  "StoreLogo"
call :generate_logo 310 150 "Wide310x150Logo"

echo.
echo Generating crisp Windows executable icon outside of the appx folder...
call :generate_ico

if "%GENERATE_SVG_OUT%"=="1" if /I "%EXT_LOWER%"=="svg" (
    call :generate_final_svg
)

:: Clean up temporary master file if one was generated
if not "%MASTER_PNG%"=="%SOURCE_FILE%" if exist "%MASTER_PNG%" del /f /q "%MASTER_PNG%"

echo.
echo Assets written to:
echo   PNGs -^> %OUTPUT_DIR%
echo   ICO  -^> %ICO_OUTPUT_DIR%\%ICO_BASE_NAME%.ico
if "%GENERATE_SVG_OUT%"=="1" if /I "%EXT_LOWER%"=="svg" echo   SVG  -^> %ICO_OUTPUT_DIR%\%ICO_BASE_NAME%.svg
exit /b 0

:: -------------------------------------------------------------------------
:: Subroutines (Parentheses Escaped using ^)
:: -------------------------------------------------------------------------
:generate_logo
set "W=%~1"
set "H=%~2"
set "NAME=%~3"
set "OUT_PATH=%OUTPUT_DIR%\%NAME%.png"

if not "%ROUND_RADIUS%"=="" (
    magick "%MASTER_PNG%" -filter Lanczos -resize %W%x%H% -gravity center -extent %W%x%H% ^( +clone -alpha transparent -background none -draw "fill white roundrectangle 0,0 %W%,%H% %ROUND_RADIUS%,%ROUND_RADIUS%" ^) -compose DstIn -composite -colorspace sRGB "%OUT_PATH%"
) else (
    magick "%MASTER_PNG%" -filter Lanczos -resize %W%x%H% -gravity center -extent %W%x%H% -colorspace sRGB "%OUT_PATH%"
)
echo Generated %NAME%.png ^(%W%x%H%^)
exit /b 0

:generate_ico
set "OUT_PATH=%ICO_OUTPUT_DIR%\%ICO_BASE_NAME%.ico"

if not "%ROUND_RADIUS%"=="" (
    set "TEMP_CLIPPED=%TEMP%\clipped_ico_%RANDOM%.png"
    magick "%MASTER_PNG%" ^( +clone -alpha transparent -background none -draw "fill white roundrectangle 0,0 4096,4096 %ROUND_RADIUS%,%ROUND_RADIUS%" ^) -compose DstIn -composite "!TEMP_CLIPPED!"
    magick "!TEMP_CLIPPED!" -filter Lanczos -define icon:auto-resize=256,48,32,16 -colorspace sRGB "%OUT_PATH%"
    if exist "!TEMP_CLIPPED!" del /f /q "!TEMP_CLIPPED!"
) else (
    magick "%MASTER_PNG%" -filter Lanczos -define icon:auto-resize=256,48,32,16 -colorspace sRGB "%OUT_PATH%"
)
echo Generated %ICO_BASE_NAME%.ico in %ICO_OUTPUT_DIR%
exit /b 0

:generate_final_svg
set "OUT_PATH=%ICO_OUTPUT_DIR%\%ICO_BASE_NAME%.svg"
echo Generating flattened, clipped final vector SVG asset...

set "SVG_RAD=0"
if not "%ROUND_RADIUS%"=="" set "SVG_RAD=%ROUND_RADIUS%"

set "TEMP_BODY=%TEMP%\svg_body_%RANDOM%.txt"
if exist "%TEMP_BODY%" del /f /q "%TEMP_BODY%"

:: Safely extract inner paths using PowerShell to completely bypass Command Prompt's parsing issues
powershell -Command "$text = Get-Content '%SOURCE_FILE%' -Raw; if ($text -match '(?s)(<g.*?>.*</g>)') { $Matches[1] | Set-Content '%TEMP_BODY%' } else { '' | Set-Content '%TEMP_BODY%' }" 2>nul

(
echo ^<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256"^>
echo   ^<defs^>
echo     ^<clipPath id="icon-clip"^>
echo       ^<rect x="0" y="0" width="256" height="256" rx="%SVG_RAD%" ry="%SVG_RAD%" /^>
echo     ^</clipPath^>
echo   ^</defs^>
echo   ^<g clip-path="url(#icon-clip)"^>
echo     ^<rect width="256" height="256" fill="%BG_COLOR%" /^>
echo     ^<g transform="translate(16, 16) scale(9.333)" fill="none"^>
if exist "%TEMP_BODY%" type "%TEMP_BODY%"
echo     ^</g^>
echo   ^</g^>
echo ^</svg^>
) > "%OUT_PATH%"

if exist "%TEMP_BODY%" del /f /q "%TEMP_BODY%"
echo Generated %ICO_BASE_NAME%.svg in %ICO_OUTPUT_DIR%
exit /b 0

powershell -Command "(Get-Content '%OUT_PATH%') -replace '<...clipPath>', '</clipPath>' | Set-Content '%OUT_PATH%'" 2>nul

if exist "%TEMP_BODY%" del /f /q "%TEMP_BODY%"
echo Generated %ICO_BASE_NAME%.svg in %ICO_OUTPUT_DIR%
exit /b 0