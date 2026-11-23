#define SuiteSource "..\..\..\dist\Meshlit-Studio"
[Setup]
AppId={{BCDB8A08-D414-4D52-B3BA-A786BD6933A6}
AppName=Meshlit Studio
AppVersion=2.0.49
AppPublisher=IMON
AppPublisherURL=https://imon.com.bd/meshlit/
DefaultDirName={localappdata}\Programs\Meshlit Studio
DefaultGroupName=Meshlit Studio
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\Meshlit.exe
OutputDir=..\..\..\dist
OutputBaseFilename=Meshlit-Studio-Windows-X64-Setup
Compression=zip/1
SolidCompression=no
WizardStyle=modern
CloseApplications=yes

[Files]
Source: "{#SuiteSource}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Meshlit Studio"; Filename: "{app}\Meshlit.exe"

[Run]
Filename: "{app}\Meshlit.exe"; Description: "Open Meshlit Studio"; Flags: nowait postinstall skipifsilent
