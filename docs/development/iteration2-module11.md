# 迭代二模块十一 通用 CSV 导出工具

本模块提供无业务依赖的 CsvExporter，输出 UTF-8 BOM CSV，供后续 StatService.exportCsv 组装报表后调用。没有新增依赖或改动 DAO、数据库结构与 Service 接口。

## 接口

```java
public Path export(Path target, List<String> headers,
                   Iterable<? extends List<?>> rows) throws IOException
```

通过 `new CsvExporter()` 创建无状态工具，可注入业务层；工具不访问数据库、不判断权限，也不决定分类/月度统计口径。
返回目标文件的绝对规范路径。target、headers、rows 均不能为 null，表头至少一列且没有 null，数据行不能为 null、列数必须与表头相等。
参数、行宽、单元类型、非有限数或 NUL 文本错误抛 IllegalArgumentException；文件或编码故障抛 IOException。工具不把 I/O 故障伪装成 DataAccessException，也没有自动转业务异常。

## 格式与单元类型

文件以 `EF BB BF` 三个 BOM 字节开头，采用 UTF-8；记录以 CRLF 结束，包括最后一行。没有数据时仍输出表头。
文本字段始终用双引号包围，内部双引号成对转义，逗号和内嵌 CR/LF 保留在字段内。非法 UTF-16 代理项触发编码故障，避免静默替换成问号。

| 输入类型 | 输出 |
| --- | --- |
| String / 表头 | 引号包围的文本，按下述规则处理危险前缀 |
| null 单元 | 空字段；与空字符串的双引号字段格式不同 |
| Boolean | true / false |
| Byte、Short、Integer、Long、BigInteger | 无分组整数，不添加文本前缀 |
| BigDecimal | toPlainString，避免 Locale 分组与小数逗号 |
| Float、Double | 有限数的 Locale 无关文本；NaN/无穷被拒绝 |
| 其他对象或自定义 Number 子类 | 拒绝，调用方先明确转换 |

数字类型按上述 JDK 具体类接受，BigDecimal/BigInteger 的自定义子类也不自动信任其格式化输出。
Service 应传入 Long/Double/BigDecimal 等实际数字，避免先拼上“件”“kg”等单位成为文本列；单位写在表头。
CSV 本身保留数值字符，但 Excel 自动识别、超长整数精度、前导零及日期识别属于表格软件行为，需要接收方选择合适导入格式，不能把本工具当作有单元格类型的 XLSX 导出。

## 公式前缀处理

文本首个有效字符为 `= + - @` 或全角对应字符时，在引号内加一个 TAB；前导空白/格式字符后出现的上述前缀也处理。以 TAB、CR、LF 开头的文本也加保护。数字类型的负数仍按数字输出。
这会改变底层文本值，例如字符串 `-2.5` 与数字 `-2.5d` 的输出不同，后续程序导入必须知道这一约定。普通文本不增加前缀，字段分隔符和双引号仍按 CSV 规则转义。
该做法参考 [OWASP CSV Injection](https://owasp.org/www-community/attacks/CSV_Injection)。不同表格软件、区域设置与再次保存后的行为可能不同，没有适用于所有下游软件的通用净化保证。本轮未启动 Excel 做 GUI 验收，也不支持导出后任意编辑仍安全的承诺。

## 文件保护与失败处理

目标由调用方选择，父目录须已存在，不自动创建目录或覆盖已有文件；扩展名由调用方决定，建议使用 .csv。
先在同目录建立本次临时文件，完整写入并关闭后，再用不带 REPLACE_EXISTING 的 Files.move 发布。测试同时覆盖调用时目标已存在，以及导出数据迭代过程中目标被创建的情况，原文件均保留。
参数/数据/编码错误、迭代器故障在暂存阶段发生时不发布目标，删除本次临时文件。清理失败附加到原异常的 suppressed，不覆盖主错误，也不删除其他操作创建的目标。
没有使用 ATOMIC_MOVE，因为该选项对已有目标的处理依赖平台。移动阶段的行为仍遵从文件系统提供者；不保证所有文件系统的并发发布原子性、掉电持久性或强制结束进程后的临时文件清理。
输入只迭代一次，按行暂存和格式化，不将整个报表装入内存。输入集合在调用期间不得并发修改。

## 对接现有统计对象

分类数据由业务层从同一份报表快照映射为列，不让 util 依赖 CategoryStat 或 StatReport：

```java
List<String> headers = List.of("分类", "在架数", "下架数", "已流转数", "成交数");
List<List<?>> rows = new ArrayList<>();
for (CategoryStat category : categories) {
    StatCounts counts = category.getCounts();
    rows.add(List.of(category.getCategoryName(), counts.getOnSaleItemCount(),
            counts.getOffShelfItemCount(), counts.getFlowedItemCount(), counts.getDealFlowCount()));
}
Path exported = new CsvExporter().export(target, headers, rows);
```

`categories` 应来自用户当前获准查看的报表结果，不要重新查询成不同时间或范围的数据。月度导出由业务层把 YearMonth 转为 String 并选择相应数值列。
调用方负责授权、字段选择和脱敏，禁止导出 User 的哈希/盐/口令等凭据。全量用户对象、异常、任意 Bean 等不属于支持的单元类型。
现有目标需要覆盖时，界面与业务层应另行安排明确的覆盖确认和策略；不能在调用工具前无条件删除旧文件。
后续 Service 还需定义导出失败的业务错误码并映射 IOException，GUI 使用后台任务执行并提示结果；当前没有完整 StatService、StatReport、路径选择器或导出进度界面。

## 验证

新增 CsvExporterTest 16 项，先确认缺失类导致失败，再实现并验证：

- BOM、中文、CRLF、引号/逗号/内嵌换行、空值、Boolean 与 Unicode 扩展字符。
- Locale 切换下的数字输出、危险前缀文本与 typed 负数的区别。
- 无数据、行宽/类型错误、非法代理项和 NUL、只迭代一次的一万行输入。
- 已有文件、导出过程目标冲突、缺失父目录和目录目标、迭代器失败后的清理。
- 实际 StatDao 分类结果映射导出，验证引用的中文分类与数值一致。

```powershell
mvn -o -B "-Dmaven.repo.local=.mvn-cache" package
```

全量 package 验证通过：此前 220 项加新增 16 项，共 236 项，失败 0、错误 0、跳过 0。格式与故障测试不等同 Excel 界面的手工验收。
独立只读审查通过，没有待修复问题；CsvExporter 已打入 JAR，字节码目标为 Java 17。删除临时文件失败的分支进行了静态检查，尚未做实际文件系统清理失败注入。
本轮未提交或推送 Git，原 Word/UML 仍保持迭代一版本。
