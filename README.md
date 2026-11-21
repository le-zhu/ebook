# ebook

基于 Jetpack Compose Material 3 和 Readium Kotlin Toolkit 的安卓本地 EPUB 阅读器。

## 本地运行

使用 Android Studio 打开包含 `settings.gradle.kts` 的目录，等待 Gradle 同步，选择 `app` 并在 Android 7.0 或更新设备上运行。

在 Windows PowerShell 中也可以执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。项目使用 Android SDK 37.1、Build Tools 37.0.0 和 Readium 3.4.0。

运行 `.\gradlew.bat :app:assembleRelease` 可生成精简正式版。正式版用 R8 裁剪未使用的代码和资源，产物位于 `app/build/outputs/apk/release/`。覆盖安装时须使用与已安装版本相同的签名。

书架的「导入文件夹」会扫描所选目录和所有子目录，将 EPUB 加入书架。文件夹里新增书后，可以再次导入；内容相同的书会自动跳过。文件管理器可以通过「打开方式」直接阅读单本。导入的副本及封面保存在应用私有目录，阅读位置、书签、书架及排版偏好由 DataStore 保存。

在阅读页的「设置」中可选择原书排版、默认排版（全局共享）或本书自定义排版。点顶部放大镜搜索本书正文，点击结果跳转；「返回原阅读位置」会回到本次第一次查看搜索结果前的位置。
