# 记账本 图标集（SVG）

App 里用到的**全部图标**，导出为可独立使用的 SVG 矢量文件。

## 为什么是"导出"而不是"复制"

这个项目的界面图标**不是**矢量资源文件，而是用 Compose `DrawScope` 手绘的
（`app/src/main/java/com/pocketledger/ui/components/LedgerIcon.kt`，31 个图标）。
当初这么选是因为 `material-icons-extended` 会带来 3 MB 依赖并把它的版本绑死在 Compose 上，
而这里所有图形都能归结为线段、圆弧和圆角矩形。

所以本目录里的 SVG 是那 31 段绘图代码的**等价转写**，坐标逐一对得上；
`generate_icons.py` 就是那份转写，是这套图标的唯一几何来源。

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
├── index.html               图标一览页（浏览器打开即可）
├── preview.png              上面那页的截图，不想开浏览器就看这个
├── MANIFEST.md              图标 ↔ 枚举 ↔ iconKey 对照表（自动生成）
└── generate_icons.py        生成脚本（几何定义在这里）
```

## 看图

- 直接打开 `preview.png`，或
- 用浏览器打开 `index.html`：每个图标给出**浅色与深色两种背景**下的效果。

## 格式约定

| 项 | 取值 |
| --- | --- |
| 画布 | 界面图标 `viewBox="0 0 100 100"`；启动图标 `0 0 108 108` |
| 坐标 | 原代码里"画布边长的几分之几" × 100，所以 `0.22` → `22` |
| 描边 | 常规 `stroke-width="8.5"`（原 `w * 0.085`），细线 `6.5`（原 `w * 0.065`） |
| 端头/拐角 | 全部 `round`（与原代码 `StrokeCap.Round` / `StrokeJoin.Round` 一致） |
| 颜色 | 统一 `#000000`，只是占位 |

**关于颜色**：App 是在运行时给图标着色的（同一个图标要能用类别色、主题色渲染），
所以源文件里的黑色没有语义。要在别处改色，两种办法：

- 把 `generate_icons.py` 里的 `INK = "#000000"` 改成目标色后重新生成；
- 或把生成文件里的 `stroke="#000000"` / `fill="#000000"` 换成 `currentColor`，
  即可跟随 CSS 的 `color`（部分设计软件不认识 `currentColor`，所以默认没有用它）。

## 重新生成

```powershell
python icons/generate_icons.py
```

会重写 `svg/`、`index.html`、`MANIFEST.md`。`preview.png` 需要手动重截：

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
