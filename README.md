# 记得记

一款个人使用、数据留在安卓手机本机的快速记账 App。最低支持 Android 8.0（API 26）。

## 第一版功能

- 两步录单：先选标签，再选预测金额或用整数键盘输入金额；
- 每次回到前台重新定位，未授予精确位置时不允许使用；
- 根据位置、时间、工作日/周末和历史记录排列 3 个推荐标签；
- 根据同标签、200 米内且近 90 天的记录推荐最多 3 个常用金额；
- 备注最多 200 字，支持系统相机和系统相册，可保存多张原图；
- 账单列表显示本周、本月实际支出，支持搜索和按周期筛选；
- 账单可编辑、硬删除，可记录多次部分退款或一键退完剩余金额；
- 自定义标签的名称、Emoji、颜色和顺序，可停用及恢复；
- 完整导出 ZIP：含四个 Sheet 的 Excel 和全部原图；
- 每次启动或从后台重新打开时检查 GitHub Release；完整下载并校验后强制进入系统安装步骤。

界面不显示经纬度和定位精度；这些原始数据仅保存在账单中，并在完整导出的 Excel 里提供。金额只使用整数人民币元。

## 隐私与权限

App 只声明：精确/大致位置、访问 GitHub 更新所需的网络权限，以及请求系统安装 APK 的权限。不申请通知、无障碍、后台定位、相机、相册/存储、短信、联系人、麦克风、悬浮窗或读取其他 App 的权限。

账单数据库和照片位于应用私有目录，Android 云备份和设备迁移备份均已禁用。完整导出是未加密 ZIP。除 GitHub Release 更新接口和安装包外，App 不主动访问其他网络服务；地点文字由安卓系统的反向地理编码能力提供。

## 在 Mac 上调试

1. 安装 Android Studio，并在 SDK Manager 中安装 Android SDK 36 和一个 Android 8.0 以上的模拟器镜像。
2. 用 Android Studio 打开本仓库，等待 Gradle 同步完成。
3. 创建并启动虚拟设备，运行 `app` 配置。
4. 在模拟器的 Extended controls → Location 中设置位置，再授予 App 精确位置。

也可以连接已开启 USB 调试的安卓手机，然后运行：

```bash
./gradlew installDebug
```

本地验证命令：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。本地 Debug 包没有配置 GitHub 仓库，因此更新检查会显示“未配置更新仓库”。

Debug 包与正式发布包的签名不同，不能直接覆盖安装。不要在 Debug 包里长期记录真实数据；卸载 Debug 包会同时清除其中的账单和照片。

## GitHub Release 与手机更新

发布版由 `.github/workflows/release.yml` 构建。先生成一把长期保存、以后绝不能更换的签名密钥：

```bash
keytool -genkeypair -v -keystore jideji-release.jks -alias jideji -keyalg RSA -keysize 4096 -validity 10000
```

在 GitHub 仓库的 Actions secrets 中配置：

- `JIDEJI_KEYSTORE_BASE64`：`base64 < jideji-release.jks | tr -d '\n'` 的输出；
- `JIDEJI_KEYSTORE_PASSWORD`；
- `JIDEJI_KEY_ALIAS`；
- `JIDEJI_KEY_PASSWORD`。

把密钥文件离线备份，不要提交到 Git。之后推送版本标签即可发布，例如：

```bash
git tag v0.1.0
git push origin v0.1.0
```

工作流会自动测试、生成同签名 APK，并按 `jideji-v版本号.apk` 命名后上传到对应的 GitHub Release。发布包会自动写入当前仓库的 owner/repo；后续发布必须使用更高版本标签，并继续使用同一把签名密钥。App 下载完成前不限制使用，安装包完整且通过包名、版本号和签名校验后才会强制更新；最终安装确认仍由安卓系统完成。
