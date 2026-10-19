# 菱粉开机动画

把任意视频转成车机可用的 `bootanimation.zip` 的 Android 应用。

**只做打包，不碰 root、不碰系统分区、不联网。**

---

## 这是干什么的

五菱车机（Android 9 厂商分支）支持从 `/data/custom-media/bootanimation.zip` 换开机动画，但安卓标准的 `bootanimation.zip` 有十几条硬性格式要求，手工做很容易踩坑：

- ZIP 必须全部 **Stored**（零压缩），用默认的 Deflate 会被车机直接拒绝
- `desc.txt` 必须在 ZIP **根目录**，ASCII + LF，不能有 BOM，不能有空行
- 帧文件名必须**纯数字**且同一 part 内**等宽**
- 每帧像素必须**逐帧**等于 `desc.txt` 声明的宽高
- 主体必须**一次播完**，写成循环会导致开机时画面反复闪烁

这个 App 把上述规则全部固化进代码，选个视频点一下就行。

## 功能（v0.4.4）

| 功能 | 说明 |
|---|---|
| 选视频 | 走系统 SAF 选择器，支持 mp4 / mov / mkv 等，自动处理旋转元数据（竖拍不会躺倒） |
| 画布 | 1920×1080（1080p 车机）/ 1280×720（720p 车机） |
| 帧率 | 固定 **25 fps**（车机标准），不可调 |
| 时长 | 连续波轮，默认停在原片时长；向左拖动加速压缩，**最低 6.0 秒**（车机开机窗口保护） |
| 画质 | 连续波轮 **50–100**，默认 85，推荐 85–92；越低体积越小 |
| 体积预估 | 真实抽帧试压 + 快速公式双通道，拖动波轮即时响应，后台算完自动修正 |
| 取消生成 | 任意进度可取消，UI 立即复原，后台线程毫秒级中断 |
| 预览 | 生成的 ZIP 可全屏预览播放，进入即显示真实总秒数 |
| 打包 | `c 1 0 part0`（主体一次播完）+ `p 0 0 part1`（1 帧末帧防黑屏） |
| 导出 | 存到「下载/LingfenBoot/」，文件名取自视频名 |
| 权限 | **零权限**（SAF + MediaStore，全程不申请存储权限） |

## 打包出来的结构

```
bootanimation.zip
├── desc.txt          1920 1080 25
│                     c 1 0 part0
│                     p 0 0 part1
├── part0/000.jpg     主体，一次播完，绝不循环
│   └── ...（N 帧）
└── part1/xxx.jpg     末帧仅 1 帧，播完保持画面防黑屏
```

## 怎么用

1. 手机装 APK（Android 9 及以上）
2. 点「选择视频」挑一段视频
3. 选画布 / 帧率 / 时长
4. 点「生成 bootanimation.zip」
5. 把「下载/LingfenBoot/」里的 zip 传到车机

## 传到车机（这一步不在 App 范围内）

车机 → 远程管理 → 启用 WEB 远控 → 手机连同一 Wi-Fi 扫码进网页端 → 底部「开机动画」→ 上传校验 → 回到车机画廊「刷新」→ 选中 → 确认更换。

> 前提：车机已激活，且 root ADB 就绪。要恢复原厂动画，画廊里有「恢复原厂动画」。

## 几个已知现象

- **开机只播前面几秒**：这是正常的。开机动画窗口约 6.5 秒，桌面就绪后动画进程就退出，动画再长也播不完。所以做长视频的话，把最精彩的部分放在**开头**。
- **首帧/末帧是黑的**：很多视频本身是淡入淡出的，属于原片设计，不是 bug。
- **时长滑不到 6 秒以下**：这是刻意保护。低于 6 秒会在车机启动完成前就播完，导致画面定格傻等。

## 接手须知

1. **代码全在 `app/src/main/java/com/lingfen/bootanim/`**，没有 native 库、没有第三方二进制依赖，纯 Java + Kotlin + Compose。
2. **改车机规范只需看两个文件**：`BootPackager.java`（desc.txt 与 ZIP 结构）、`FrameExtractor.java`（抽帧与缩放）。
3. **改 UI 只需看 `ui/` 目录**，`MainScreen.kt` 是主页面，`AppState.kt` 是全部状态。
4. **版本号在 `app/build.gradle`**：改 `versionCode` 与 `versionName` 即可。
5. **CI 在 `.github/workflows/build.yml`**：推到 main 自动出 APK，产物在 Actions 的 Artifacts 里。
6. **本仓库不携带 Gradle Wrapper**（`gradlew` / `gradle-wrapper.jar`），这是刻意的。若你想用 wrapper，本机装好 Gradle 后执行 `gradle wrapper` 即可自动生成。

## 构建

本仓库只包含纯源码，不携带任何构建工具二进制。三种方式任选：

```bash
# 方式一：Android Studio 直接 Open 本目录，Sync 后 Build APK

# 方式二：本机已装 Gradle 8.7+（或 Android Studio 自带 Gradle）
gradle assembleRelease

# 方式三：推到 GitHub，Actions 会自动装好 Gradle 并出 APK（见 .github/workflows/build.yml）
```

产物在 `app/build/outputs/apk/release/`。

## 技术实现

- 纯 Java + AndroidX + Compose，**无 native 依赖、无 FFmpeg**，APK 约 1–2 MB，任何 CPU 架构都能跑
- 抽帧：`MediaMetadataRetriever.getFrameAtTime()`（微秒级）+ `Canvas` 等比缩放居中裁切
- 打包：手写 ZIP 写入器（`StoredZipWriter`），精确控制每个字节——因为 `java.util.zip.ZipOutputStream` 默认 DEFLATE，不符合规范
- 存储：**API 29+ 走 MediaStore，零权限**。注意：`minSdk` 虽为 28，但为了做到全程零权限弹窗，Manifest 已不声明存储权限；因此 API 28 设备保存到公共下载目录会失败。若要支持 Android 9 手机，需在 `AndroidManifest.xml` 补回 `WRITE_EXTERNAL_STORAGE`（带 `android:maxSdkVersion="28"`）。

## License

MIT