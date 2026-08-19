# Windows EXE 安装包打包说明

本文说明如何在 Windows 上使用仓库内的 Inno Setup，将 VRCMC 的 Compose Desktop release 应用打包为单文件 EXE 安装程序。

## 环境要求

- 64 位 Windows。
- JDK 21，并正确设置 `JAVA_HOME`。可执行 `java -version` 检查。
- 能正常运行仓库自带的 Gradle Wrapper。
- Inno Setup 编译器位于 `.gradle\tools\innosetup\ISCC.exe`。

以下命令均在仓库根目录的 PowerShell 中执行。

## 1. 确认版本号

发布前确认以下两个版本号一致：

- `gradle\libs.versions.toml` 中的 `app-version`，用于应用自身和 Compose Desktop 包。
- `installer\VRCMC.iss` 中的 `AppVersion`，用于安装包名称和安装信息。

例如当前版本均为 `1.1.2`。

## 2. 生成 release 应用目录

```powershell
.\gradlew.bat :composeApp:createReleaseDistributable
```

需要忽略已有任务缓存、强制完整重建时执行：

```powershell
.\gradlew.bat :composeApp:createReleaseDistributable --rerun-tasks
```

成功后，待打包的完整应用目录为：

```text
composeApp\build\compose\binaries\main-release\app\VRCMC\
```

该目录包含 `VRCMC.exe`、应用依赖和裁剪后的 Java 运行时。不能只复制其中的 `VRCMC.exe`。

## 3. 使用仓库内 Inno Setup 打包

```powershell
.\.gradle\tools\innosetup\ISCC.exe .\installer\VRCMC.iss
```

看到 `Successful compile` 即表示打包成功。输出文件为：

```text
composeApp\build\installer\VRCMC-v<版本号>-setup.exe
```

当前版本对应：

```text
composeApp\build\installer\VRCMC-v1.1.2-setup.exe
```

`installer\VRCMC.iss` 会把 release 应用目录整体压入安装包，并配置开始菜单、可选桌面快捷方式、卸载入口以及英语、简体中文、繁体中文、日语安装界面。

## 4. 校验产物

确认文件存在并计算 SHA-256：

```powershell
$installer = Get-Item .\composeApp\build\installer\VRCMC-v1.1.2-setup.exe
$installer | Select-Object FullName, Length, LastWriteTime
Get-FileHash -Algorithm SHA256 $installer.FullName
```

发布前建议在未安装 VRCMC 的 Windows 测试环境中走一遍安装、启动、覆盖升级和卸载流程。安装包目前未配置代码签名，因此 Windows 可能显示 SmartScreen 提示。

## 常见问题

### 找不到 release 应用目录

先确认第 2 步成功执行。Inno Setup 脚本读取的是 `main-release`，普通的 `createDistributable` 生成的 `main` 目录不能替代它。

### 找不到 `ISCC.exe`

确认 `.gradle\tools\innosetup\ISCC.exe` 存在。`.gradle` 属于本机 Gradle 工作目录，不会提交到 Git；如果清理过该目录，需要先恢复这套工具后再执行打包命令。

### 安装包版本仍是旧版本

同时更新 `gradle\libs.versions.toml` 的 `app-version` 和 `installer\VRCMC.iss` 的 `AppVersion`，然后重新执行第 2、3 步。

### 修改脚本后找不到相对路径

从仓库根目录执行命令，并保留 `installer\VRCMC.iss` 中相对于 `installer` 目录的资源路径和输出路径。
