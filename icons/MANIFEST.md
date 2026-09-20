# 图标清单（由 `generate_icons.py` 生成，请勿手改）

界面图标 **31** 个，启动图标 3 个（背景 / 前景 / 合成）。

「长边」是图标实际着色范围（含描边宽度的一半）在 100×100 画布上的较长边。
全部落在 64–84 之间：超出上限的缩进来，低于下限的放大到下限，其余原样居中。

| 文件 | LedgerIcon | iconKey | 分组 | 长边 |
| --- | --- | --- | --- | --- |
| `svg/list.svg` | `LIST` | — | 导航 | 64.5 |
| `svg/chart.svg` | `CHART` | — | 导航 | 71 |
| `svg/calendar.svg` | `CALENDAR` | `calendar` | 导航 | 80.5 |
| `svg/search.svg` | `SEARCH` | — | 导航 | 72.5 |
| `svg/wallet.svg` | `WALLET` | `wallet` | 导航 | 80.5 |
| `svg/person.svg` | `PERSON` | — | 导航 | 68.6 |
| `svg/plus.svg` | `PLUS` | — | 导航 | 64 |
| `svg/settings.svg` | `SETTINGS` | — | 导航 | 66.5 |
| `svg/chevron-right.svg` | `CHEVRON_RIGHT` | — | 导航 | 64 |
| `svg/ellipsis.svg` | `ELLIPSIS` | — | 导航 | 65 |
| `svg/food.svg` | `FOOD` | `food` | 支出大类 | 84 |
| `svg/transport.svg` | `TRANSPORT` | `transport` | 支出大类 | 80.5 |
| `svg/shopping.svg` | `SHOPPING` | `shopping` | 支出大类 | 69.5 |
| `svg/home.svg` | `HOME` | `home` | 支出大类 | 80.5 |
| `svg/comms.svg` | `COMMS` | `comms` | 支出大类 | 76.5 |
| `svg/study.svg` | `STUDY` | `study` | 支出大类 | 72.5 |
| `svg/campus.svg` | `CAMPUS` | `campus` | 支出大类 | 84 |
| `svg/fun.svg` | `FUN` | `fun` | 支出大类 | 64 |
| `svg/medical.svg` | `MEDICAL` | `medical` | 支出大类 | 76 |
| `svg/gift.svg` | `GIFT` | `gift` | 支出大类 | 84 |
| `svg/finance.svg` | `FINANCE` | `finance` | 支出大类 | 68.5 |
| `svg/work.svg` | `WORK` | `work` | 支出大类 | 80.5 |
| `svg/pet.svg` | `PET` | `pet` | 支出大类 | 64 |
| `svg/other.svg` | `OTHER` | `other` | 支出大类 | 64 |
| `svg/income.svg` | `INCOME` | `income` | 收入与账户 | 66.5 |
| `svg/cash.svg` | `CASH` | `cash` | 收入与账户 | 84 |
| `svg/card.svg` | `CARD` | `card` / `prepaid` | 收入与账户 | 84 |
| `svg/bank.svg` | `BANK` | `bank` | 收入与账户 | 84 |
| `svg/alipay.svg` | `ALIPAY` | `alipay` | 收入与账户 | 80 |
| `svg/wechat.svg` | `WECHAT` | `wechat` | 收入与账户 | 80 |
| `svg/tag.svg` | `TAG` | — | 兜底 | 78.5 |
| `svg/launcher-background.svg` | — | — | 启动图标 | — |
| `svg/launcher-foreground.svg` | — | — | 启动图标 | — |
| `svg/launcher.svg` | — | — | 启动图标（合成） | — |

## 两个品牌标识不是手绘的

其余图标都是从 `LedgerIcon.kt` 的绘图代码转写的（描边 + 坐标 = 画布边长的比例），
下面两个是真实品牌标识，描摹自 `source/` 里的 PNG，因此是**填充路径**而非描边，
多出来的部分（微信气泡里的对勾、支付宝「支」字内部的封闭空间）用 `fill-rule="evenodd"` 挖空。

- `svg/alipay.svg`（`ALIPAY`）：由 `source/alipay.png``（96×96）描摹，2 条轮廓 / 106 点
- `svg/wechat.svg`（`WECHAT`）：由 `source/wechat.png``（359×359）描摹，1 条轮廓 / 69 点
