# 记账本 图标集（SVG）

App 里用到的**全部图标**，导出为可独立使用的 SVG 矢量文件。

## 为什么是"导出"而不是"复制"

这个项目的界面图标**不是**矢量资源文件，而是用 Compose `DrawScope` 手绘的
（`app/src/main/java/com/pocketledger/ui/components/LedgerIcon.kt`，31 个图标）。
当初这么选是因为 `material-icons-extended` 会带来 3 MB 依赖并把它的版本绑死在 Compose 上，
而这里所有图形都能归结为线段、圆弧和圆角矩形。

所以本目录里的 SVG 是那 31 段绘图代码的**等价转写**，坐标逐一对得上；
`generate_icons.py` 就是那份转写，是这套图标的唯一几何来源。

**两个例外：微信支付与支付宝是真实品牌标识**，不能用"照抄一段绘图代码"的方式得到，
所以是从 PNG **描摹**出来的（见下）。

> ⚠️ 这些文件**没有接入 App**，App 仍然用 Canvas 画图。
> 日后改动 `LedgerIcon.kt` 的图形时，**这个文件夹不会自动跟着变** ——
> 请同步改 `generate_icons.py` 并重新生成，否则两边会不一致。

## 目录

```
icons/
├── svg/                     34 个 SVG
│   ├── list.svg … tag.svg   31 个界面图标（与 LedgerIcon 枚举一一对应）
│   ├── launcher.svg         启动图标合成预览
│   ├── launcher-background.svg
│   └── launcher-foreground.svg
├── source/                  两个品牌标识的原始 PNG（描摹的输入）
├── logos.json               描摹结果：归一化后的轮廓点（由 trace_logos.py 生成）
├── trace_logos.py           PNG → 轮廓的描摹器
├── index.html               图标一览页（浏览器打开即可）
├── preview.png              上面那页的截图，不想开浏览器就看这个
├── MANIFEST.md              图标 ↔ 枚举 ↔ iconKey 对照表（自动生成）
└── generate_icons.py        生成脚本（手绘那 29 个的几何定义在这里）
```

## 看图

- 直接打开 `preview.png`，或
- 用浏览器打开 `index.html`：每个图标给出**浅色与深色两种背景**下的效果。

## 两个品牌标识（描摹，不是手绘）

`svg/wechat.svg`（微信支付）和 `svg/alipay.svg`（支付宝）来自 `source/` 里的 PNG，
由 `trace_logos.py` 描摹成 SVG：

| 文件 | 来源 | 描摹结果 |
| --- | --- | --- |
| `svg/wechat.svg` | `source/wechat.png`（359×359） | 1 条轮廓 / 69 点 |
| `svg/alipay.svg` | `source/alipay.png`（96×96） | 2 条轮廓 / 106 点 |

和其余图标的三点不同：

1. **是填充路径，不是描边**：`stroke="none" fill="#000000"`。其余图标是"中心线 + 8.5 描边"。
2. **挖空用 `fill-rule="evenodd"`**：微信气泡里的对勾、支付宝「支」字内部那圈封闭空间，
   在源文件里是透明的，靠 evenodd 变成洞；否则会被填死。
3. **无背景**：源文件本身就是透明底，SVG 里也没有任何背景矩形，可以直接叠在任意底色上用。

**尺寸**：把所有轮廓的最长边缩放到 **80 单位**并居中（画布仍是 100×100）。
不是照着其余图标实测的 ~84.5 单位 ink 盒子对齐 —— 描边是以中心线为准向两侧各溢出约 4 单位，
而这几个是实心块；若也做到 84.5，视觉上会比任何其他图标都重。80 才是那些描边图标"被画进"
的盒子，是更诚实的视觉等重。

**保真度**：把 SVG 和源 PNG 都按 ink 外框归一化后栅格化对比，
微信 IoU ≈ 0.989、支付宝 IoU ≈ 0.977，实心面积差约 0.5%（也就是边缘位置差在零点几个像素，
远小于图标实际显示尺寸）。**无背景、形状不失真、边框位置没有整体偏移**是这三项各自量过的。

**重新描摹**：

```powershell
python icons/trace_logos.py      # 读 source/*.png → 重写 logos.json
python icons/generate_icons.py   # 读 logos.json → 重写 svg/wechat.svg、svg/alipay.svg
```

描摹流程（覆盖率 → 必要时的双线性放大 → marching squares 取半覆盖等值线 →
Douglas-Peucker 抽稀 → 归一化）写在 `trace_logos.py` 的文档注释里，包括
"为什么放大用双线性而不是 Lanczos"（Lanczos 在硬边上过冲，会把等值线整体推出去约 1%）。


## 格式约定

| 项 | 取值 |
| --- | --- |
| 画布 | 界面图标 `viewBox="0 0 100 100"`；启动图标 `0 0 108 108` |
| 坐标 | 原代码里"画布边长的几分之几" × 100，所以 `0.22` → `22` |
| 描边 | 常规 `stroke-width="8.5"`（原 `w * 0.085`），细线 `6.5`（原 `w * 0.065`） |
| 端头/拐角 | 全部 `round`（与原代码 `StrokeCap.Round` / `StrokeJoin.Round` 一致） |
| 颜色 | 统一 `#000000`，只是占位 |
| 例外 | `wechat.svg` / `alipay.svg` 是实心填充（见上） |

**关于颜色**：App 是在运行时给图标着色的（同一个图标要能用类别色、主题色渲染），
所以源文件里的黑色没有语义。要在别处改色，两种办法：

- 把 `generate_icons.py` 里的 `INK = "#000000"` 改成目标色后重新生成；
- 或把生成文件里的 `stroke="#000000"` / `fill="#000000"` 换成 `currentColor`，
  即可跟随 CSS 的 `color`（部分设计软件不认识 `currentColor`，所以默认没有用它）。

## 重新生成

```powershell
python icons/generate_icons.py
```

会重写 `svg/`、`index.html`、`MANIFEST.md`。两个品牌标识的几何来自 `logos.json`，
所以改了 `LedgerIcon.kt` 的手绘图标之后直接跑这一条就够了；只有重新描摹时才需要先跑
`trace_logos.py`。`preview.png` 需要手动重截：

```powershell
chrome --headless=new --disable-gpu --hide-scrollbars --window-size=1400,1400 `
  --screenshot=icons/preview.png "file:///<绝对路径>/icons/index.html"
```

## 与代码的对应关系

- 枚举名 → 文件名：`CHEVRON_RIGHT` → `chevron-right.svg`（下划线转连字符，全小写）。
- `LedgerIcon.forKey()` 把数据库里的 `iconKey` 映射到枚举，完整对照见 `MANIFEST.md`。
  例如类别 `餐饮` 存的是 `food`，对应 `svg/food.svg`。
- 启动图标同时存在于 `app/src/main/res/drawable/ic_launcher_*.xml`
  （Android VectorDrawable）。这里额外转成 SVG 并加了合成图，
  是为了让整个文件夹只用一种格式。
- 两个品牌标识与 `LedgerIcon.WECHAT` / `.ALIPAY` 对应，`iconKey` 分别是
  `wechat` / `alipay`。注意 `LedgerIcon.kt` 里这两个目前画的还是**手绘近似图形**
  （两个气泡 / 卡片加几条线），与这里的品牌标识**不一致** —— 本目录只是提供矢量素材，
  没有改 App；要不要换成品牌标识由后续决定。
