# 通用 CSV 导出工具设计

用户授权继续开发 CsvExporter，落实 FR-17 的 UTF-8 BOM、中文与数值导出格式能力。原设计只有工具类名称，没有冻结方法签名。本轮只实现无业务依赖的文件导出，不新增 StatService/StatReport/GUI。

## API 与格式

- util.CsvExporter 为无状态 final 类，公开无参构造器，公开 `Path export(Path target,List<String> headers,Iterable<? extends List<?>> rows) throws IOException`。
- 返回目标绝对规范路径。目标父目录必须已存在；不自动建目录，不限制扩展名，不覆盖任何已有目标（含导出过程中被其他操作创建的目标）。
- target/headers/rows 非 null，headers 至少一列，表头单元非 null；数据行非 null 且与表头等宽。非法参数抛 IllegalArgumentException，文件系统故障抛 IOException，cause 不做业务转换。
- UTF-8 编码，文件开头一个 BOM，记录使用 CRLF，包括最后一条记录。空数据集合仍输出 BOM 和表头。
- 文本（表头与 String）始终使用双引号，内部双引号成对转义，保留逗号与 CR/LF。null 单元输出空字段。
- 数字支持 Byte/Short/Integer/Long/BigInteger/BigDecimal/Float/Double，按无分组、与 Locale 无关的格式输出，不加文本前缀；BigDecimal 用 toPlainString，Float/Double 须有限。Boolean 输出 true/false。其他类型拒绝，调用方先明确转换为受支持类型。
- 普通文本原文输出；危险公式前缀文本在引号内加一个 TAB。识别 = + - @ 及全角同类前缀，并检查前导空白/格式字符后的前缀；以 TAB/CR/LF 起始的文本也加保护。此行为改变底层文本，typed Number 的负数不会按公式文本处理。
- 不声称通用电子表格安全策略或 Excel 二次保存后仍绝对安全，未进行 Excel GUI 验收。参考 OWASP CSV Injection：https://owasp.org/www-community/attacks/CSV_Injection。
- 编码器拒绝非法 UTF-16 代理项，不静默替换字符。NUL 文本拒绝，避免下游截断。

## 文件与失败处理

先验证目标与表头，在同目录生成临时文件，逐行写入并关闭后再移动到目标，移动不使用 REPLACE_EXISTING 或 ATOMIC_MOVE。
发生行宽/类型/编码错误、迭代器错误、I/O 失败或提交冲突时删除本次临时文件；保留原异常，清理失败附加为 suppressed，不删除已存在目标。
同目录暂存避免数据尚未写完就出现目标文件，但不保证掉电持久性或所有文件系统上的原子发布。进程被强制杀死可能遗留临时文件。
按 Iterable 单次迭代流式输出，不缓存全部数据。调用期间输入集合不得并发修改；每行可作局部快照。调用方负责数据范围、字段脱敏、目标路径选择和覆盖确认策略。

## 实现选择与边界

采用 JDK 文件与编码 API，避免新增 CSV 库依赖；固定表头/等宽二维数据适合当前小组报表。
没有把 CategoryStat/MonthStat/StatReport 写进 util，保留层次边界，由业务层把选定报表列映射为表头与行。
配置解析、减碳计算、报表口径、导出失败业务错误码与 GUI 文件选择器留给后续接入。本轮不提交或推送 Git。
