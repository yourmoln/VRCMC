#define AppName "VRCMC"
#define AppVersion "1.1.0"
#define SourceDir "..\composeApp\build\compose\binaries\main-release\app\VRCMC"

[Setup]
AppId={{6FE18FBC-6E62-4D4E-8F4F-3B44CEDF45ED}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=VRCM Team
AppPublisherURL=https://github.com/yourmoln/VRCMC
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\composeApp\build\installer
OutputBaseFilename=VRCMC-{#AppVersion}-setup
SetupIconFile=..\composeApp\src\desktopMain\resources\VRCMC.ico
UninstallDisplayIcon={app}\VRCMC.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "chinesesimplified"; MessagesFile: "ChineseSimplified.isl"
Name: "chinesetraditional"; MessagesFile: "ChineseTraditional.isl"
Name: "japanese"; MessagesFile: "Japanese.isl"

[CustomMessages]
english.CreateDesktopShortcut=Create a desktop shortcut
chinesesimplified.CreateDesktopShortcut=创建桌面快捷方式
chinesetraditional.CreateDesktopShortcut=建立桌面捷徑
japanese.CreateDesktopShortcut=デスクトップショートカットを作成
english.AdditionalShortcuts=Additional shortcuts:
chinesesimplified.AdditionalShortcuts=附加快捷方式：
chinesetraditional.AdditionalShortcuts=其他捷徑：
japanese.AdditionalShortcuts=追加のショートカット：
english.LaunchVrcmc=Launch VRCMC
chinesesimplified.LaunchVrcmc=启动 VRCMC
chinesetraditional.LaunchVrcmc=啟動 VRCMC
japanese.LaunchVrcmc=VRCMC を起動

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopShortcut}"; GroupDescription: "{cm:AdditionalShortcuts}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\VRCMC"; Filename: "{app}\VRCMC.exe"
Name: "{autodesktop}\VRCMC"; Filename: "{app}\VRCMC.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\VRCMC.exe"; Description: "{cm:LaunchVrcmc}"; Flags: nowait postinstall skipifsilent
