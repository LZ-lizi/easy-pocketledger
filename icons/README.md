# 记账本 图标集

App 里用到的**全部图标**，既是可独立使用的 SVG，也是 App 真正加载的 VectorDrawable 的来源。

## 这套图标现在是怎么进 App 的

以前界面图标是用 Compose `DrawScope` 手绘的（`ui/components/LedgerIcon.kt`，495 行绘图代码），
选它是因为 `material-icons-extended` 会带来 3 MB 依赖并把版本绑死在 Compose 上。
**从 v0.8.4 起改成矢量资源。**

**`svg/` 是手绘原稿，任何脚本都不会写它。** 从 v0.8.5 起管线是：

```
icons/svg/            手绘原稿（人改这里，脚本只读）
      │  normalize_icons.py   统一尺寸与粗细
      ▼
icons/normalized/     规范化后的 100×100 SVG
      │  make_drawables.py    逐字搬运 pathData
      ▼
app/src/main/res/drawable/ic_ledger_*.xml
```

`LedgerIcon.kt` 现在只剩枚举、`forKey` 映射和一个 `when` 表达式（枚举 → 资源 id）——
用表达式是为了让「加了枚举却没给文件」直接编译失败。

**两个图标是真实品牌标识**（微信支付、支付宝），不能用"照抄一段绘图代码"的方式得到，
所以是从 PNG **描摹**出来的（见下）。

`generate_icons.py` 是**最初那套程序化几何**的留存，输出改到 `icons/drawn/`，只用于对照，
不再参与管线 —— 它过去会重写整个 `svg/` 目录，覆盖掉手改的图标，所以被移开了。

## 目录

```
icons/
├── svg/                     手绘原稿，35 个 SVG（脚本只读，永不覆写）
│   ├── list.svg … tag.svg   32 个界面图标
│   ├── launcher.svg         启动图标合成预览
│   ├── launcher-background.svg
│   └── launcher-foreground.svg
├── normalized/              规范化后的 32 个 UI 图标（管线的实际产物）
├── source/                  两个品牌标识的原始 PNG（描摹的输入）
├── logos.json               描摹结果：归一化后的轮廓点（由 trace_logos.py 生成）
├── normalize_icons.py       统一尺寸与粗细（svg/ → normalized/）
├── trace_logos.py           PNG → 轮廓的描摹器
├── make_drawables.py        normalized/ → app/src/main/res/drawable/ic_ledger_*.xml
├── generate_icons.py        最初的程序化几何，输出到 drawn/，仅供对照
├── index.html               图标一览页（浏览器打开即可，含 24/32/40 像素小样）
├── preview.png              上面那页的截图，不想开浏览器就看这个
├── preview-compare.png      左＝svg/ 原稿，右＝normalized/，改了什么一眼可见
└── _work/                   规范化过程的中间产物（接触表、对比图，已 gitignore）
```

## 看图

- 直接打开 `preview.png`，或
- 用浏览器打开 `index.html`：每个图标给出 72 像素大图和 24 / 32 / 40 像素小样，
  用来确认缩小到实际显示尺寸后线条依然清楚。

## 统一尺寸与粗细

原稿是从好几个来源凑起来的：24×24 的图标库、32×32 的电话、50×50 和 512×512 的
Illustrator 导出、以及 100×100 的旧绘图。每个文件都撑在自己的画布上，所以**画得一样大
的路径，实际着色范围差到 1.44 倍**（在 320 像素的格子里量，长边 204 – 294）。
先把这个修掉，再修由它带来的第二个问题。

| | 原稿 | 规范化后 |
| --- | --- | --- |
| 墨迹长边（同一格子，单位 = 格子/100） | 64.0 – 92.0 | **76 – 81**（目标 78） |
| 线条视觉粗细 | 5.9 – 23.7 | **7.9 – 9.0**（描边类），实心块另计 |

**尺寸**：量的是**实际着色的墨迹**（不是路径坐标），把长边缩放到 **78/100**，再居中。
`normalize_icons.py` 会把这个数字印出来。

**粗细**：统一尺寸本身会改变粗细 —— 缩放图纸会连线条一起缩放，原本只占了画布一小块的
图标就会被放大成"粗线"。所以缩放之后要把笔重新发一遍，基准线宽回到 **8.5/100**。
两条路线，取决于粗细藏在哪儿：

- **`vector`（默认）**：图形由描边路径构成时，重写 `stroke-width` 即可，中心线一个点都不动，
  端头和圆弧原样保留。同时，对"整体是一块实心"的图标（银行、微信气泡、三个点、人物头像）
  也走这条：实心块没有"线宽"可调，往里收只会把形状吃掉。
  - 一条路径本身是描边、但只是 **0.5 单位头发丝细节**（比如钞票图标上的 ¥）时不重写笔宽，
    否则那条细节会被放大 17 倍糊成一块。判据是：最粗的描边至少占图形墨迹的 40%。
- **`mask`**：图形由**填充轮廓**构成时，线宽已经焊死在形状里。这类图标只有在
  **比目标细**、或**比目标粗且不含实心块**时，才从自身 700 像素位图重新描一遍：
  把轮廓整体**向外推**或**向内收**线宽差值的一半，中心线不动，形状其他部分不动。
  - 向外推永远安全，上限 1.6/100；向内收可能吃掉细节，上限 1.0/100。
  - 整块实心（墨迹离边缘比任何线条都远）的图标**不允许内收**，否则那是改形状不是改粗细。

**保真度**：走 `mask` 路线的是 `alipay cash comms ellipsis fun gift hospital other pet wallet`
共 10 个。把重新描摹的结果和它本该是的那个位图各自按墨迹外框归一化后对比，
**IoU 0.987 – 0.994，逐像素差异 0.10% – 0.77%**（见 `_work/verify_masks.py`）。
走 `vector` 路线的图标是仿射变换 + 重发笔宽，中心线在数学上完全没动。

**为什么不做成"全都一样粗"**：实心块（银行 16.5、微信 22.7、人物头像）和本身就是粗块的
图形（三个点 11.8、设置页的圆点）没有线宽这个自由量；把它们削到 8.5 是在改形状。
描边类图标最终落在 **7.9 – 9.0**，同一族里已看不出差别。

## 两个品牌标识（描摹，不是手绘）

`svg/wechat.svg`（微信支付）和 `svg/alipay.svg`（支付宝）来自 `source/` 里的 PNG，
由 `trace_logos.py` 描摹而成。

| 文件 | 来源 | 描摹结果 |
| --- | --- | --- |
| `svg/wechat.svg` | `source/wechat.png`（359×359） | 1 条轮廓 / 69 点 |
| `svg/alipay.svg` | `source/alipay.png`（96×96） | 2 条轮廓 / 106 点 |

和其余图标的三点不同：

1. **是填充路径，不是描边**：`stroke="none" fill="#000000"`。
2. **挖空用 `fill-rule="evenodd"`**：微信气泡里的对勾、支付宝「支」字内部那圈封闭空间，
   在源文件里是透明的，靠 evenodd 变成洞；否则会被填死。
3. **无背景**：源文件本身就是透明底，SVG 里也没有任何背景矩形，可以直接叠在任意底色上用。

描摹流程（覆盖率 → 必要时的双线性放大 → marching squares 取半覆盖等值线 →
Douglas-Peucker 抽稀 → 归一化）写在 `trace_logos.py` 的文档注释里，包括
"为什么放大用双线性而不是 Lanczos"（Lanczos 在硬边上过冲，会把等值线整体推出去约 1%）。

## 格式约定

| 项 | 取值 |
| --- | --- |
| 画布 | `normalized/` 与界面图标一律 `viewBox="0 0 100 100"`；启动图标 `0 0 108 108` |
| 墨迹长边 | 78/100 |
| 基准线宽 | 8.5/100，细线按同一比例缩放 |
| 端头/拐角 | 全部 `round`（VectorDrawable 里写在每条 path 上，它不继承根节点） |
| 颜色 | 统一 `#000000`，只是占位 |
| 摆放 | 缩放到位后套一层 `<g transform="translate(a,b) scale(s)">`；可为嵌套 |
| 实心图标 | 单条 `<path fill-rule="evenodd">`，一个 path 装下所有轮廓 |

**关于颜色**：App 在运行时给图标着色（同一个图标要能用类别色、主题色渲染），
所以源文件里的黑色没有语义；资源文件里也是黑色，靠 `Icon(tint = …)` 覆盖。

## 重新生成

```powershell
python icons/normalize_icons.py    # svg/ → normalized/ + preview.png + index.html
python icons/make_drawables.py     # normalized/ → app/src/main/res/drawable/ic_ledger_*.xml
```

两条都要跑，App 才会跟着变。`normalize_icons.py --dry-run` 只测量并打印
每个图标走哪条路线、缩放多少、最终粗细是多少，不写任何文件。

自查用：

```powershell
python icons/_work/compare.py 400       # 左：svg/ 原稿，右：normalized/，并给出重合度
python icons/_work/verify_masks.py      # 重描的那 10 个是否忠实
```

品牌标识的几何来自 `logos.json`，只有重新描摹时才需要先跑 `trace_logos.py`
（读 `source/*.png`），它已经并入 `logos.json`，再由 `normalize_icons.py` 统一处理。

## 与代码的对应关系

- 枚举名 → 文件名：`CHEVRON_RIGHT` → `chevron-right.svg`（下划线转连字符，全小写）；
  → 资源名：`ic_ledger_chevron_right`（连字符转下划线，资源名不允许连字符）。
- `LedgerIcon.forKey()` 把数据库里的 `iconKey` 映射到枚举。
  例如类别 `餐饮` 存的是 `food`，对应 `svg/food.svg` 与 `ic_ledger_food.xml`。
- 启动图标同时存在于 `app/src/main/res/drawable/ic_launcher_*.xml`（Android VectorDrawable，
  手写而非生成）。这里额外转成 SVG 并加了合成图，是为了让整个文件夹只用一种格式。
- 两个品牌标识与 `LedgerIcon.WECHAT` / `.ALIPAY` 对应（`iconKey` 为 `wechat` / `alipay`）：
  它们在 App 里就是这里描摹出来的那两条填充路径（`fillType="evenOdd"`）。
