# 记账本 图标集

App 里用到的**全部图标**，既是可独立使用的 SVG，也是 App 真正加载的 VectorDrawable 的来源。

## 这套图标现在是怎么进 App 的

以前界面图标是用 Compose `DrawScope` 手绘的（`ui/components/LedgerIcon.kt`，495 行绘图代码），
选它是因为 `material-icons-extended` 会带来 3 MB 依赖并把版本绑死在 Compose 上。
**从 v0.8.4 起改成矢量资源**：`icons/generate_icons.py` 里的几何同时产出

1. `icons/svg/*.svg` —— 本目录的导出件（看效果用），
2. `app/src/main/res/drawable/ic_ledger_*.xml` —— App 实际加载的 VectorDrawable，
   由 `icons/make_drawables.py` 从 (1) 转换而来，**路径数据逐字搬运**。

也就是说：**这里就是唯一几何来源**。改图形改 `generate_icons.py`，然后跑两个脚本，
App 与预览页同时更新，两边不可能不一致。`LedgerIcon.kt` 现在只剩枚举、`iconKey` 映射
和一个 `when` 表达式（枚举 → 资源 id）—— 用表达式是为了让「加了枚举却没给文件」直接编译失败。

**两个图标是真实品牌标识**（微信支付、支付宝），不能用"照抄一段绘图代码"的方式得到，
所以是从 PNG **描摹**出来的（见下）。

## 目录

```
icons/
├── svg/                     34 个 SVG（几何的可读形态）
│   ├── list.svg … tag.svg   31 个界面图标（与 LedgerIcon 枚举一一对应）
│   ├── launcher.svg         启动图标合成预览
│   ├── launcher-background.svg
│   └── launcher-foreground.svg
├── source/                  两个品牌标识的原始 PNG（描摹的输入）
├── logos.json               描摹结果：归一化后的轮廓点（由 trace_logos.py 生成）
├── trace_logos.py           PNG → 轮廓的描摹器
├── generate_icons.py        几何定义 + 统一尺寸 + 产出 SVG
├── make_drawables.py        SVG → app/src/main/res/drawable/ic_ledger_*.xml
├── index.html               图标一览页（浏览器打开即可）
├── preview.png              上面那页的截图，不想开浏览器就看这个
└── MANIFEST.md              图标 ↔ 枚举 ↔ iconKey ↔ 实际长边（自动生成）
```

## 看图

- 直接打开 `preview.png`，或
- 用浏览器打开 `index.html`：每个图标给出**浅色与深色两种背景**下的效果。
  每张卡片下面还标了该图标的实际着色长边（见下）。

## 统一尺寸：收紧散布，不是拉成一样大

每个图标按**实际着色范围**（含描边宽度的一半）测量，长边超出 `[64, 84]` 的按比例缩放
到边界、其余只做居中。实测原本是 **52.0 – 88.5**，其中校园卡 88.5 几乎顶到画布边缘；
现在全部落在带内，**20 个图标一个像素没动**。

为什么不统一到一个数：缩放会**连描边一起缩放**，一只 56.5 单位的右箭头（本来就该小、
就坐在列表行里）拉到 84 之后比旁边的银行卡还重。要修的是离群，不是差异。
`MANIFEST.md` 里每个图标都标了实际长边。

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
| 坐标 | 原绘图代码里"画布边长的几分之几" × 100，所以 `0.22` → `22` |
| 描边 | 常规 `stroke-width="8.5"`，细线 `6.5`；统一尺寸时按同一比例缩放 |
| 端头/拐角 | 全部 `round`（VectorDrawable 里写在每条 path 上，它不继承根节点） |
| 颜色 | 统一 `#000000`，只是占位 |
| 例外 | `wechat.svg` / `alipay.svg` 是实心填充 + `fill-rule="evenodd"`（见上） |
| 摆放 | 长边超出 `[64, 84]` 的图标套一层 `<g transform>` / `<group>` 缩放居中 |

**关于颜色**：App 在运行时给图标着色（同一个图标要能用类别色、主题色渲染），
所以源文件里的黑色没有语义；资源文件里也是黑色，靠 `Icon(tint = …)` 覆盖。
要在别处改色，两种办法：

- 把 `generate_icons.py` 里的 `INK = "#000000"` 改成目标色后重新生成；
- 或把生成文件里的 `stroke="#000000"` / `fill="#000000"` 换成 `currentColor`，
  即可跟随 CSS 的 `color`（部分设计软件不认识 `currentColor`，所以默认没有用它）。

## 重新生成

```powershell
python icons/generate_icons.py     # 几何 → svg/ + index.html + MANIFEST.md
python icons/make_drawables.py     # svg/  → app/src/main/res/drawable/ic_ledger_*.xml
```

两条都要跑，App 才会跟着变。品牌标识的几何来自 `logos.json`，
只有重新描摹时才需要先跑 `trace_logos.py`（读 `source/*.png`）。`preview.png` 手动重截：

```powershell
chrome --headless=new --disable-gpu --hide-scrollbars --window-size=1200,1500 `
  --screenshot=icons/preview.png "file:///<绝对路径>/icons/index.html"
```

## 与代码的对应关系

- 枚举名 → 文件名：`CHEVRON_RIGHT` → `chevron-right.svg`（下划线转连字符，全小写）；
  → 资源名：`ic_ledger_chevron_right`（连字符转下划线，资源名不允许连字符）。
- `LedgerIcon.forKey()` 把数据库里的 `iconKey` 映射到枚举，完整对照见 `MANIFEST.md`。
  例如类别 `餐饮` 存的是 `food`，对应 `svg/food.svg` 与 `ic_ledger_food.xml`。
- 启动图标同时存在于 `app/src/main/res/drawable/ic_launcher_*.xml`（Android VectorDrawable，
  手写而非生成）。这里额外转成 SVG 并加了合成图，是为了让整个文件夹只用一种格式。
- 两个品牌标识与 `LedgerIcon.WECHAT` / `.ALIPAY` 对应（`iconKey` 为 `wechat` / `alipay`）：
  它们在 App 里就是这里描摹出来的那两条填充路径（`fillType="evenOdd"`），
  不再是以前的手绘近似图形。
