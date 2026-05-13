# TG Anime (Android, Compose + Media3)

A Kotlin / Jetpack Compose front-end for the `tg_anime` Telegram-anime backend.

## 功能

- **发现** — bgm.tv 本季新番海报网格,按星期切换,点击海报自动跳搜索
- **搜索** — 调 search.acgn.es 公开索引,按集数自动分组,每集列出全部源(字幕组 / 画质 / 大小)
- **播放** — ExoPlayer (Media3) 自动播放,带:
  - 双击屏幕左/右 ±10 秒
  - 长按 2.0× 倍速(松开恢复)
  - 顶部下拉选倍速:0.5× / 0.75× / 1× / 1.25× / 1.5× / 2×
  - 左半屏竖直滑动调亮度
  - 右半屏竖直滑动调音量
  - 水平拖动快进 / 后退(带预览)
  - 底部进度条 + 播放暂停 + ±10 秒按钮
  - 强制横屏 + 保持屏幕常亮
- **设置** — 配置后端地址(指向你 PC 上跑的 `python -m tg_anime`)

## 数据流

```
bgm.tv /calendar          ← 发现页 (本季周历)
search.acgn.es /api/      ← 搜索页 (公开 TG 频道索引,带集数 / 画质 / 字幕组解析)
tg_anime /by_link/<ch>/<id> ← 播放页 (TG 文件流式代理,带 Range,直接喂给 ExoPlayer)
```

## 构建

要求:JDK 17、Android SDK(`compileSdk 34`),Android Studio 任意近版(Iguana 以上即可)。

### 选项一:在 Android Studio 里

1. `File → Open` 选这个目录
2. 等 Gradle sync 完成
3. `Run` 按钮跑 `app` 模块,或者菜单里 `Build → Build APK(s)` 出 debug APK

### 选项二:命令行

需要先在项目根目录跑一次 `gradle wrapper --gradle-version 8.9` 生成 `gradlew`(我没法在 zip 里附带 `gradle-wrapper.jar` 因为版本兼容问题,所以请你自己生成一次):

```bash
# 一次性
gradle wrapper --gradle-version 8.9

# 之后每次:
./gradlew :app:assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

或者直接用系统 `gradle`:
```bash
gradle :app:assembleDebug
```

## 跑起来后

1. 装 APK,允许"安装未知来源应用"
2. 启动后默认进入「发现」页(可能加载几秒拉 bgm.tv)
3. 先去「设置」填入后端地址,例如 `http://192.168.31.20:8080`(改成你 PC 在局域网里的 IP + tg_anime 端口),点「测试连接」确认 OK,再「保存」
4. 回到「发现」点海报 → 自动跳搜索 → 自动列结果 → 选集 → 选源 → 播放
5. 也可以直接在「搜索」自己手动输关键词

## 已知限制 / 后续可加

- 没做"我的收藏"或"播放历史"
- 没做画中画(PIP)
- 没做选集详情页(直接列在搜索结果里)
- 没做 4K HEVC 软解兜底(直接走 ExoPlayer 默认硬解,绝大多数设备能播)
- App 端没集成 `/tv/search` 那条 bot 路径(用了 acgn 公开索引,数据量更大、速度更快、无需 telethon)

## 后端要求

需要 `tg_anime`(就是你那个 FastAPI 项目)跑起来,而且必须监听局域网(`uvicorn.host = "0.0.0.0"` 不是 `127.0.0.1`)。`/by_link/<channel>/<msg_id>` 这条路由必须返回流式视频带 `Accept-Ranges: bytes`。

App 端依赖的端点:
- `GET /health` — 设置页的连接测试用
- `GET /by_link/{channel}/{msg_id}` — 实际播放
