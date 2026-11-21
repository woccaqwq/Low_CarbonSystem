"""Create the iteration-two Word design document using the configured Python runtime.

Run from the repository with bundled python-docx/Pillow. Image rasterization is a
separate build step using the configured Node/sharp runtime. No product code runs.
"""
from pathlib import Path
from datetime import datetime, timezone
from collections import Counter
import json
import re
import textwrap
import argparse
import subprocess
from html import escape
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "target" / "design-word"
FIGURES = BUILD / "figures"
DEST = ROOT / "文本文档" / "04_迭代2_系统设计说明书.docx"
FONT = "Microsoft YaHei"

def svg_text(x, y, text, size=15, anchor="start", bold=False):
    return f'<text x="{x}" y="{y}" font-size="{size}" text-anchor="{anchor}" font-weight="{"bold" if bold else "normal"}">{escape(text)}</text>'

def make_figures():
    FIGURES.mkdir(parents=True, exist_ok=True)
    nodes = [
        ("User", ["userId : int", "role : Role", "creditScore : int"], 20, 20),
        ("Item", ["ownerId : int", "categoryId : Integer", "price : double / status"], 305, 20),
        ("Category", ["categoryId : int", "parentId : Integer", "name : String"], 590, 20),
        ("Favorite", ["userId / itemId", "createdAt"], 20, 285),
        ("FlowRecord", ["sellerId / buyerId", "status / finishedAt"], 305, 285),
        ("Review", ["flowId / fromUserId", "toUserId / score"], 590, 285),
    ]
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="820" height="495" viewBox="0 0 820 495"><rect width="820" height="495" fill="white"/><style>text{font-family:Microsoft YaHei,sans-serif;fill:#111}</style>']
    for name, fields, x, y in nodes:
        parts += [f'<rect x="{x}" y="{y}" width="210" height="125" fill="white" stroke="#526b82"/>',
                  f'<rect x="{x}" y="{y}" width="210" height="31" fill="#e8eef4" stroke="#526b82"/>',
                  svg_text(x + 105, y + 22, name, 17, "middle", True)]
        for index, field in enumerate(fields):
            parts.append(svg_text(x + 10, y + 56 + index * 24, field, 14))
    lines = [(230, 76, 305, 76, "1", "0..*"), (590, 76, 515, 76, "0..1", "0..*"),
             (125, 145, 125, 285, "1", "0..*"), (410, 145, 125, 285, "1", "0..*"),
             (410, 145, 410, 285, "1", "0..*"), (515, 348, 590, 348, "1", "0..2")]
    for x1, y1, x2, y2, tail, head in lines:
        reverse = y1 == y2 and x1 > x2
        parts += [f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#526b82"/>',
                  svg_text(x1 - 8 if reverse else x1 + 8, y1 + 17, tail, 12, "end" if reverse else "start"),
                  svg_text(x2 + 8 if reverse else x2 - 8, y2 - 7, head, 12, "start" if reverse else "end")]
    parts += [svg_text(20, 450, "只展示关键字段；完整字段与双角色关联见迭代二图册。", 15),
              svg_text(20, 476, "评价 0..2 为业务上限，仍须 Service 校验交易双方。", 15), '</svg>']
    (FIGURES / "domain.svg").write_text("\n".join(parts), encoding="utf-8")
    def sequence(name, labels, events):
        width = 720
        xs = [70 + i * (580 / (len(labels) - 1)) for i in range(len(labels))]
        height = 110 + len(events) * 65
        parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="720" height="{height}"><rect width="720" height="{height}" fill="white"/><style>text{{font-family:Microsoft YaHei,sans-serif;fill:#111}}</style><defs><marker id="a" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto"><path d="M0 0 L7 3.5 L0 7Z" fill="#425e78"/></marker></defs>']
        for index, label in enumerate(labels):
            x = xs[index]
            parts += [f'<rect x="{x - 62}" y="8" width="124" height="45" rx="3" fill="{"#fff2d2" if index == 0 else "#e8eef4"}" stroke="#526b82"/>',
                      svg_text(x, 36, label, 15, "middle", True),
                      f'<line x1="{x}" y1="53" x2="{x}" y2="{height - 10}" stroke="#a9b5c0" stroke-dasharray="5 4"/>']
        for index, (start, end, text) in enumerate(events):
            y = 82 + index * 65
            parts += [f'<rect x="9" y="{y - 19}" width="702" height="26" fill="white"/>',
                      svg_text(18, y, f"{index + 1}  {text}", 15),
                      f'<line x1="{xs[start]}" y1="{y + 19}" x2="{xs[end]}" y2="{y + 19}" stroke="#425e78" marker-end="url(#a)"/>']
        parts.append('</svg>')
        (FIGURES / f"{name}.svg").write_text("\n".join(parts), encoding="utf-8")
    sequence("deal", ["服务 待实现", "连接", "流转DAO", "物品DAO", "用户与日志"], [
        (0, 1, "获取连接并开启事务，所有查询与写入传同一 conn"),
        (0, 2, "查流转并校验参与人、当前身份、PENDING 与在架状态"),
        (0, 2, "updateStatus(DEAL)：必须命中 1 行"),
        (0, 3, "updateStatus(FLOWED)：必须命中 1 行"),
        (0, 2, "cancelOtherPending：允许 0 行，没有其他意向也可成交"),
        (0, 4, "双方信用各 +1，分别检查 1 行；双方各写一条业务日志"),
        (0, 1, "成功 commit；任一失败 rollback；最后关闭连接"),
    ])
    sequence("review", ["服务 待实现", "连接", "流转DAO", "评价DAO", "用户与日志"], [
        (0, 1, "开启事务；资格查询、评价与信用写入均使用 conn"),
        (0, 2, "校验 DEAL、交易双方、七天窗口；推导评价接收人"),
        (0, 3, "插入评价；UNIQUE(flow_id, from_user_id) 防重复"),
        (0, 4, "仅接收人信用：4–5 星 +2，1–2 星 -3，3 星不变"),
        (0, 4, "写评价业务日志；不再次给予成交双方 +1"),
        (0, 1, "成功 commit；任一失败 rollback；最后关闭连接"),
    ])

def set_font(run, size=None, bold=None, name=FONT):
    run.font.name = name
    run.font.color.rgb = RGBColor(0, 0, 0)
    rpr = run._element.get_or_add_rPr()
    fonts = rpr.find(qn('w:rFonts'))
    if fonts is None:
        fonts = OxmlElement('w:rFonts'); rpr.insert(0, fonts)
    for key in ('ascii', 'hAnsi', 'eastAsia', 'cs'):
        fonts.set(qn('w:' + key), name)
    if size: run.font.size = Pt(size)
    if bold is not None: run.bold = bold

def configure(doc):
    section = doc.sections[0]
    section.page_width, section.page_height = Cm(21), Cm(29.7)
    section.top_margin, section.bottom_margin = Cm(1.8), Cm(1.7)
    section.left_margin, section.right_margin = Cm(2), Cm(2)
    section.footer_distance = Cm(.7)
    for name, size, bold in [('Normal', 10.5, False), ('Title', 22, True), ('Subtitle', 15, False),
                             ('Heading 1', 16, True), ('Heading 2', 12, True), ('Heading 3', 11, True)]:
        style = doc.styles[name]
        style.font.name = FONT; style.font.size = Pt(size); style.font.bold = bold
        style.font.italic = False
        style.font.color.rgb = RGBColor(0, 0, 0)
        for border in list(style.element.iter(qn('w:pBdr'))):
            border.getparent().remove(border)
        if name == 'Subtitle':
            for numbering in list(style.element.iter(qn('w:numPr'))):
                numbering.getparent().remove(numbering)
        fonts = style.element.get_or_add_rPr().find(qn('w:rFonts'))
        if fonts is None: fonts = OxmlElement('w:rFonts'); style.element.get_or_add_rPr().append(fonts)
        fonts.set(qn('w:eastAsia'), FONT)
        fmt = style.paragraph_format
        fmt.space_after = Pt(6); fmt.line_spacing = 1.22
        if name.startswith('Heading'):
            fmt.space_before = Pt(9); fmt.keep_with_next = True
    p = section.footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run('第 '); set_font(r, 9)
    fld = OxmlElement('w:fldSimple'); fld.set(qn('w:instr'), 'PAGE'); p._p.append(fld)
    r = p.add_run(' 页'); set_font(r, 9)
    doc.core_properties.title = '低碳校园资源循环流转系统迭代二系统设计说明书'
    doc.core_properties.subject = '数据层实现与业务接入设计'
    doc.core_properties.author = '系统设计与数据层负责人'
    doc.core_properties.created = datetime(2026, 11, 16, tzinfo=timezone.utc)
    doc.core_properties.modified = datetime(2026, 11, 16, tzinfo=timezone.utc)

def paragraph(doc, text, bold_lead=None):
    p = doc.add_paragraph()
    if bold_lead and text.startswith(bold_lead):
        set_font(p.add_run(bold_lead), bold=True); set_font(p.add_run(text[len(bold_lead):]))
    else: set_font(p.add_run(text))
    return p

def bullet(doc, text):
    p = paragraph(doc, '• ' + text)
    p.paragraph_format.left_indent = Cm(.15)
    p.paragraph_format.space_after = Pt(4)
    return p

def heading(doc, text, page=False, level=1):
    p = doc.add_paragraph(text, style='Heading ' + str(level))
    if page: p.paragraph_format.page_break_before = True
    return p

def table(doc, headers, rows, widths, size=9.5, compact=False):
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER; t.autofit = False
    for column, width in zip(t.columns, widths): column.width = Cm(width)
    for cell, width in zip(t.rows[0].cells, widths): cell.width = Cm(width)
    props = t._tbl.tblPr
    borders = OxmlElement('w:tblBorders')
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        e = OxmlElement('w:' + edge); e.set(qn('w:val'), 'single'); e.set(qn('w:sz'), '4'); e.set(qn('w:color'), 'D9D9D9'); borders.append(e)
    props.append(borders)
    margins = OxmlElement('w:tblCellMar')
    for side, val in [('top', '45' if compact else '80'), ('bottom', '45' if compact else '80'), ('left', '95'), ('right', '95')]:
        e = OxmlElement('w:' + side); e.set(qn('w:w'), val); e.set(qn('w:type'), 'dxa'); margins.append(e)
    props.append(margins)
    for row in rows: t.add_row()
    for index, values in enumerate([headers] + list(rows)):
        tr = t.rows[index]
        if index == 0:
            repeat = OxmlElement('w:tblHeader'); tr._tr.get_or_add_trPr().append(repeat)
        no_split = OxmlElement('w:cantSplit'); tr._tr.get_or_add_trPr().append(no_split)
        for col, value in enumerate(values):
            cell = tr.cells[col]; cell.width = Cm(widths[col]); cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            if index == 0:
                shade = OxmlElement('w:shd'); shade.set(qn('w:fill'), 'E8EEF4'); cell._tc.get_or_add_tcPr().append(shade)
            p = cell.paragraphs[0]; p.paragraph_format.space_after = Pt(1 if compact else 2); p.paragraph_format.space_before = Pt(1 if compact else 2); p.paragraph_format.line_spacing = 1.12
            set_font(p.add_run(str(value)), size, index == 0)
    after = doc.add_paragraph(); after.paragraph_format.space_after = Pt(3); after.paragraph_format.line_spacing = .5
    return t

def code(doc, text, size=9.3):
    for raw in text.strip('\n').split('\n'):
        wrapped = textwrap.wrap(raw, width=94, subsequent_indent='    ', break_long_words=False, break_on_hyphens=False) or ['']
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(1); p.paragraph_format.line_spacing = Pt(11.7)
        p.paragraph_format.left_indent = Cm(.12)
        set_font(p.add_run('\n'.join(wrapped)), size, name='Consolas')

def figure(doc, name, caption, width=16.8):
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    picture = p.add_run().add_picture(str(FIGURES / f'{name}.png'), width=Cm(width))
    picture._inline.docPr.set('descr', caption)
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_font(p.add_run(caption), 9.5)
    p.paragraph_format.space_after = Pt(8)

def build():
    doc = Document(); configure(doc)
    p = doc.add_paragraph('低碳校园资源循环流转系统', style='Title')
    doc.add_paragraph('迭代二系统设计说明书', style='Subtitle')
    paragraph(doc, '本说明书说明已实现的数据层与公共工具，以及业务层接入时应遵循的接口、事务和数据口径。当前代码可独立测试；业务 Service、会话管理与 GUI 尚未完成。')
    table(doc, ['项目', '本版信息'], [
        ['文档版本', 'V1.1  迭代二实现补充'], ['编写角色', '系统设计与数据层负责人'],
        ['编写日期', '2026年11月16日'], ['代码基线', '1b3ae6f  feature/iteration2-data'],
        ['覆盖范围', '模块一至十二实现及模块十三设计同步'], ['评审状态', '供小组核对接口与接入方案，未替代三方正式确认']], [3, 14])
    heading(doc, '阅读说明', level=2)
    paragraph(doc, '本版保留迭代一需求、Word、原图和 SQL，不重写历史冻结版本。现有 Java 源为类型与方法签名依据，需求规格说明书为业务规则依据。')
    bullet(doc, '数据访问能力已实现：九个 DAO、SQLite 安全初始化、参数绑定、分页与同连接事务。')
    bullet(doc, '公共能力已实现：口令工具、会话快照、业务异常、配置读取、CSV 导出和运行日志。')
    bullet(doc, '业务接入仍待实现：认证状态、权限、交易资格、业务编排、报表与界面，不据工具存在宣称需求全量完成。')
    paragraph(doc, '正文先说明架构、模型与数据库，再说明接口、事务、关键流程和公共工具；附录列出全部 DAO 签名。完整十张图和可编辑源位于 docs/design/iteration2。')

    heading(doc, '1 系统架构与实现边界', True)
    paragraph(doc, '系统采用表示层、业务层、数据访问层与实体层，并提供不依赖业务实现的公共工具和异常。依赖由上向下，GUI 不得直接访问 DAO，DAO 之间不互调或自行编排业务事务。')
    table(doc, ['层次', '职责', '当前状态'], [
        ['GUI', '表单、列表、后台任务和提示', '待接入'], ['Service', '认证授权、资格校验、事务编排、报表', '待接入'],
        ['DAO', '查询、持久化、约束和结果映射', '九接口及实现已提供'], ['Entity', '数据载体、自身不变量、值对象', '十六个实体或值对象及四个枚举'],
        ['util / exception', '连接、口令、文件输出、日志、异常契约', '已提供，业务调用待接入']], [2.4, 9.5, 5.1])
    heading(doc, '1.1 数据层结构', level=2)
    paragraph(doc, 'DaoFactory 按接口返回惰性单例。DAO 不持有长连接；普通方法通过 DBUtil 获取独立连接并关闭，Connection 重载只管理本次 Statement 和 ResultSet。BaseDao 统一参数绑定、资源释放和 SQL 异常包装。')
    paragraph(doc, '九个实现类均继承 BaseDao 并实现对应接口。StatDaoImpl 的行计数映射为 StatCounts，分类、月份和概览由只读 SQL 组装，不应误认为 StatDao 对应额外业务表。')
    heading(doc, '1.2 已落地的设计机制', level=2)
    bullet(doc, 'DBUtil 默认连接工厂为惰性单例，同时允许 forPath 注入测试数据库。')
    bullet(doc, 'DaoFactory 以接口隔离实现；构造器注入 DBUtil 便于脱离 GUI 测试。')
    bullet(doc, '排序策略由 ItemQuery 验证并冻结为安全 ORDER BY，避免直接拼接外部文本。')
    bullet(doc, 'BaseDao 提供资源管理模板；PageResult、统计值和 SessionUser 使用明确的快照语义。')
    paragraph(doc, 'ServiceFactory、SessionManager、观察者式界面刷新等仍是后续设计，当前没有相应运行实现。')

    heading(doc, '2 领域模型与状态', True)
    figure(doc, 'domain', '图1 领域模型关键字段与业务关系')
    paragraph(doc, 'Item.price 实际为 double，数据库为 REAL；categoryId、parentId 使用 Integer，允许 null。图中查询昵称、标题和信用投影不等同历史快照，也不通过投影回写关联表。')
    paragraph(doc, 'Review 的 0至2 表示双方各一次的业务期望。数据库只保证同一流转、同一发起人唯一；必须由 Service 校验评价人属于交易双方，不能把表约束当作人数上限。')
    table(doc, ['对象', '状态方向与责任'], [
        ['ItemStatus', 'ON_SALE 可到 OFF_SHELF 或 FLOWED；OFF_SHELF 可重新 ON_SALE；FLOWED 为终态。实体守卫不替代 Service 校验。'],
        ['FlowStatus', '只有 PENDING 可到 DEAL 或 CANCELLED；两者为终态。DAO 条件 SQL 辅助拒绝重复终态更新。'],
        ['SessionUser', '仅五个不可变安全字段；不含凭据，不证明登录或实时权限；业务变化后需刷新。']], [3.2, 13.8])

    heading(doc, '3 数据库与首次初始化', True)
    table(doc, ['表', '主要关系与约束'], [
        ['sys_user', '用户名唯一；PBKDF2 哈希与盐；信用 0至150；状态 0正常或1禁用。'],
        ['category', '可空父分类；同父名称唯一，DAO 补充根分类重名保护；父分类删除受限制。'],
        ['item', '持有人引用用户，分类可空；删除分类后物品分类置空；价格非负、状态与交易方式受约束。'],
        ['favorite', '(user_id,item_id) 唯一；用户或物品删除时级联清理。'],
        ['flow_record', '引用物品与交易双方，不允许自流转；引用删除受限制。完成时间由触发器维护。'],
        ['review', '(flow_id,from_user_id) 唯一；评分 1至5、不自评；对应流转删除时级联。'],
        ['operation_log', '操作人可空；用户删除后置空并保留日志；无编辑或删除 DAO。'],
        ['sys_config', '配置键、原文值、更新时间；只读 DAO 对异常空键和非法日期报数据访问异常。']], [3.2, 13.8])
    heading(doc, '3.1 初始化与已有数据保护', level=2)
    paragraph(doc, 'DBUtil 每次创建独立文件连接，启用外键和 5000毫秒 busy_timeout。首次空库启用 WAL，在同一事务执行原建表及初始化脚本，校验必要对象与外键，并写 user_version=1。')
    paragraph(doc, '已有版本1数据库保留业务数据并检查必要表、视图和触发器。未标版本的已有库、更高版本或缺失对象会被拒绝，不自动重跑含清库命令的脚本。当前没有自动迁移旧库的能力。')
    paragraph(doc, '两份 SQL 是唯一初始化源，由 Maven 原样复制到 classpath/sql。默认库为工作目录 data/campus.db，可通过 campus.db.path 指定；命令行 SQLite 工具不是运行依赖。')
    heading(doc, '3.2 时间与金额', level=2)
    paragraph(doc, '业务时间统一 LocalDateTime，持久化为本地秒精度文本。物品更新时间、流转完成时间由原触发器辅助维护；sys_config 没有更新时间触发器，未来写入方需显式维护。金额使用 double/REAL，格式化展示与业务精度策略仍需使用方遵循。')

    heading(doc, '4 数据访问接口与返回约定', True)
    api_md = (ROOT / 'docs/design/iteration2/dao-api.md').read_text(encoding='utf-8')
    api_sections = re.findall(r'## (\w+)\s+.*?```java\n(.*?)```', api_md, re.S)
    counts = []
    for name, block in api_sections:
        sigs = [line for line in block.splitlines() if line.strip()]
        ext = sum(bool(re.search(r'\(Connection\s', line)) for line in sigs)
        counts.append([name, str(len(sigs) - ext), str(ext)])
    assert len(counts) == 9 and sum(int(r[1]) + int(r[2]) for r in counts) == 112
    table(doc, ['DAO 接口', '普通方法', 'Connection 重载'], counts + [['合计', '56', '56']], [10, 3.5, 3.5])
    paragraph(doc, '原设计第4.2节明确列出的普通签名保留，新增连接重载落实多 DAO 同事务。Category、Favorite、OperationLog 原文未给完整签名，本轮按模块说明补齐；SysConfigDao 是新增只读接口。全部112个签名见附录A。')
    heading(doc, '4.1 通用约定', level=2)
    bullet(doc, '单条可缺失查询返回 Optional.empty，列表返回空集合而不是 null。')
    bullet(doc, 'insert 返回主键，不回填输入对象；收藏重复插入返回原主键，日志重复写入仍新增。')
    bullet(doc, '页码1起、每页10至100；越界请求返回空本页并保留总数，offset 使用 long。')
    bullet(doc, 'PageResult 只保证容器快照，不能据此认定 User、Item 等可变元素被深拷贝。')
    bullet(doc, '物品默认仅 ON_SALE，显式 status(null) 才不限；收藏显示物品当前状态，不自动移除下架/已流转项。')
    bullet(doc, '更新行数由业务判定。必需单行状态或信用更新应为1；取消其他意向的数量可以为0。')

    heading(doc, '5 异常契约与固定提示', True)
    table(doc, ['类型', '继承与用途'], [
        ['BusinessException', 'RuntimeException；持有 ErrorCode，固定用户消息，可保留 cause。'],
        ['ValidationException', 'BusinessException 子类；field 为固定字段标识，null 表示整个表单。'],
        ['DataAccessException', '独立 RuntimeException；包装 SQL/IO 数据库故障，保留 cause，不是业务异常子类。']], [4.8, 12.2])
    enum_source = (ROOT / 'src/main/java/com/campus/recycle/exception/ErrorCode.java').read_text(encoding='utf-8')
    errors = re.findall(r'^    ([A-Z_]+)\("([^"]+)"\)', enum_source, re.M)
    assert len(errors) == 13
    table(doc, ['错误码', '固定提示'], errors, [7.2, 9.8], 9.2, compact=True)
    paragraph(doc, '用户不存在与口令错误提示相同；界面不得再显示不同内部错误码、cause或堆栈。字段校验异常先定位控件，再处理一般业务异常。文件导出/运行日志保留 IOException，接入层尚需定义恰当的业务提示策略。')
    paragraph(doc, 'DataAccessException 构造器仍为 (String,Throwable)。原设计示例的 (ErrorCode,Throwable) 不能直接调用；不能把 DB_ERROR 当作所有文件错误的自动映射。')

    heading(doc, '6 事务与连接管理', True)
    paragraph(doc, '一个原子业务由 Service 打开一个连接并管理事务。事务内的读取、资格校验、状态修改、信用更新与业务日志必须传同一个 conn。普通 DAO 方法使用另一个连接，看不到未提交写入。')
    code(doc, '''try (Connection conn = database.getConnection()) {
    conn.setAutoCommit(false);
    try {
        // Validate and invoke all participating DAO overloads with conn.
        conn.commit();
    } catch (RuntimeException | SQLException failure) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        throw failure;
    }
}''', 10)
    paragraph(doc, '上述为接入模板，不是已存在的 Service。DataAccessException 是非受检异常，只 catch SQLException 会漏掉它；失败后不要通过 setAutoCommit(true) 意外提交残留写入。')
    table(doc, ['操作', '行数或一致性要求'], [
        ['确认成交状态', 'updateStatus(DEAL) 必须命中1；0表示终态或同物品已有成交，必须中止。'],
        ['物品与信用单条更新', '存在且获准修改的对象应命中1；否则作为业务冲突/缺失处理。'],
        ['取消其他待确认意向', 'cancelOtherPending 返回实际取消数，允许0；成交者是唯一意向时也可成功。'],
        ['多份统计读取', '同连接读事务共享快照；分类、月份和概览单份结果内部用单条SQL读取。']], [5.3, 11.7])
    paragraph(doc, '运行日志和 CSV 文件不属于 SQLite 事务。数据库提交后的文件故障不能被误报为数据库回滚；记录已有业务异常时，日志失败应作为 suppressed 保留，不能覆盖原故障。')

    heading(doc, '7 登录与会话接入', True)
    paragraph(doc, 'UserService 与 GUI 尚未实现。推荐接入顺序为输入与失败锁定检查、按用户名读取、PasswordUtil 验证、账号可用性判断，再完成登录时间和业务日志事务，最后返回安全快照。')
    table(doc, ['阶段', '责任与边界'], [
        ['口令处理', 'PBKDF2WithHmacSHA256，100000次、256位输出、16字节随机盐，Base64。工具不检查密码强度。'],
        ['失败规则', '不存在与密码错误对外同提示；原设计连续5次失败锁定10分钟等策略待 UserService/会话管理实现。'],
        ['提交记录', 'updateLastLogin 与 LOGIN 业务日志同连接提交，更新时间检查1行；失败整体回滚。'],
        ['并发变化', '事务内重新读取当前账号；禁用/角色/凭据已变化时重新校验或拒绝，不能仅信任旧对象。'],
        ['SessionUser', '复制 userId、username、nickname、role、creditScore；无哈希、盐或原 User 引用。']], [3.3, 13.7])
    heading(doc, '7.1 会话快照的使用', level=2)
    paragraph(doc, 'SessionUser 的构造器和 from(User) 不执行认证。isAdmin 只描述快照角色，可用于菜单展示；重要操作仍需从受信任的当前身份重新读取账号和权限。SessionManager 尚未提供。')
    paragraph(doc, '信用、角色或昵称改变后应重新查询并替换快照。不能将 User 的认证字段直接交给 GUI、CSV 或日志，也不能将任意界面提交的 SessionUser 当作可信身份。')
    heading(doc, '7.2 登录事务失败的处理', level=2)
    paragraph(doc, '输入、认证、账号和锁定失败应转换为固定业务提示。登录时间或业务日志写入失败时回滚，不返回成功会话；关闭连接前不恢复自动提交。完整登录接入时序见图册04。')

    heading(doc, '8 确认成交接入', True)
    figure(doc, 'deal', '图2 确认成交成功顺序及统一失败出口')
    paragraph(doc, 'FlowService 尚未实现。图示步骤在同一事务执行；任何资格失败、必需单行更新未命中或 SQL/运行时异常都进入回滚。先确认流转更新成功，才能修改物品、取消其他意向和调整信用。')
    paragraph(doc, '信用双方各加1属于成交事务。按原需求 UC-07，双方各写一条关键动作日志。cancelOtherPending 要求保留记录属于该物品且已成交，取消数量0是合法结果。完整分支见图册06。')

    heading(doc, '9 评价与信用接入', True)
    figure(doc, 'review', '图3 评价与接收人信用同事务')
    paragraph(doc, 'ReviewService 尚未实现。发起人必须为当前受信任用户且是交易双方之一；接收人从流转记录推导，不接受界面任意指定。还需检查成交、七天窗口、每人一次、评分1至5与内容最多200码点。')
    paragraph(doc, 'exists 查询只辅助提示，UNIQUE(flow_id,from_user_id) 才是重复写入最终约束。每条评价仅影响其接收人：4至5星加2、1至2星减3、3星不变。不能把旧设计“更新双方信用”解释为每条评价同时修改两人。')
    paragraph(doc, '评价、接收人信用、业务日志使用同一 conn 提交或回滚。UserDao 原子增量将信用限制在0至150，避免基于旧对象计算后覆盖并发修改；DAO 不自动编排这些调用。完整分支见图册07。')

    heading(doc, '10 统计与配置口径', True)
    paragraph(doc, '统计 DAO 已实现，StatService、StatReport、减碳估算与 GUI 尚未实现。分类按当前直接归属分组，不重复汇总父分类；必要时包含未分类组。物品与意向分别聚合，避免一件物品的多条意向重复增加物品数。')
    table(doc, ['指标', '口径'], [
        ['物品数量', '按物品 created_at 筛日期，反映该期间发布物品的当前 ON_SALE/OFF_SHELF/FLOWED 状态，不是历史库存快照。'],
        ['意向与成交', 'PENDING 按 created_at；DEAL/CANCELLED 按 finished_at，缺失时回退 created_at。总意向数不等于成交数。'],
        ['月份', '一次 countByMonth 只返回指定年份12个月，空月补零；跨年范围需 Service 逐年读取并合并。'],
        ['用户卡片', '当前注册用户数与平均信用，包括管理员及禁用账号；随 userId 变化，不随物品/流转日期变化。'],
        ['个人范围', '物品为本人发布；流转为本人作为买家或卖家参与。范围由身份/权限决定，不能只信任界面参数。']], [3.2, 13.8])
    heading(doc, '10.1 配置读取', level=2)
    table(doc, ['键', '初始值', '后续使用方责任'], [
        ['stat.co2.per_item_kg', '2.4', '解析为适当数值，检查有限、非负与缺失策略。'],
        ['db.backup.enabled', 'true', '明确接受 true/false；读取开关不代表备份已实现。'],
        ['db.backup.keep_days', '7', '校验整数与允许范围；不是运行日志保留策略的动态配置。']], [7.4, 2, 7.6], 9)
    paragraph(doc, 'SysConfigDao 无缓存，原文返回，不把空串或坏值当作默认。需要配置与统计一致时，使用同连接读事务；缺失/损坏系数不能静默当作0或写死2.4。FR-16 的节约金额口径与配置写入仍需后续设计。')

    heading(doc, '11 文件输出与运行日志', True)
    heading(doc, '11.1 CSV 导出工具', level=2)
    paragraph(doc, 'CsvExporter 接收目标、表头和等宽二维数据，输出 UTF-8 BOM 与 CRLF。文本始终加引号并转义内部引号，保留逗号和换行；null为空字段，Boolean及白名单数字按固定格式输出。数字必须有限，BigDecimal 用 toPlainString。')
    paragraph(doc, '危险公式前缀文本加 TAB，数字类型的负数仍为数字。该策略会改变底层文本，不是跨软件或再次保存后的通用保证；尚未启动 Excel 做人工验收。超长整数精度等由表格软件导入行为决定。')
    paragraph(doc, '同目录暂存完成后发布，不请求覆盖已有文件，不自动创建父目录。数据/编码/迭代失败时尝试清理本次暂存文件，清理失败附 suppressed；移动阶段遵从文件系统，不保证任意提供者上的原子发布和掉电保存。')
    paragraph(doc, 'StatService 应导出当前获准报表的快照，选择数值列和安全字段；不能重新扩大范围或输出凭据。IOException 与输入错误的业务提示、覆盖确认和 GUI 后台执行尚待接入。')
    heading(doc, '11.2 运行日志工具', level=2)
    paragraph(doc, 'AppLogger 使用独立 JUL Logger，不修改 root 配置。DEBUG默认关闭，INFO记录关键动作，WARN记录业务拒绝/可恢复问题，ERROR记录需要人工介入的故障；每条记录同步写入并flush，包含时间、级别、类、方法、用户、动作和结果。')
    paragraph(doc, '日志按本地日期追加 logs/app-yyyyMMdd.log。启动和日期变化时保留当天及前六天，只删除名字有效且匹配的过期普通文件，不递归、不跟随链接，不删除未来/无效/其他文件。无后台零点任务或大小轮转。目录文件锁防止多个实例同时写入。')
    paragraph(doc, '字段中的控制字符转义。异常记录类型、全部栈帧和 cause/suppressed，不输出异常消息；调用方仍需保证元数据安全。文件故障抛 IOException，不覆盖原业务异常；App负责关闭。当前未自动接入 DAO/Service，不能代替事务内 operation_log。')

    heading(doc, '12 验证结果与交接', True)
    paragraph(doc, '模块十二代码基线在 Windows11、Maven3.6.3、OracleJDK23.0.1 下完成 package：27个测试类、253项测试，失败/错误/跳过均为0。编译目标为Java17，未单独在JDK17运行验收。模块十三和本次 Word 属于文档变更，未把历史结果写成本轮重跑。')
    table(doc, ['测试范围', '项目数', '测试范围', '项目数'], [
        ['数据库基础', '12', '用户与公共对象', '37'], ['分类与物品', '35', '收藏', '15'],
        ['流转', '23', '评价', '20'], ['统计', '20', '操作日志', '17'],
        ['跨 DAO 验收', '3', '会话与异常', '18'], ['配置读取', '20', 'CSV', '16'],
        ['运行日志', '17', '合计', '253']], [6.2, 2.3, 6.2, 2.3])
    paragraph(doc, '跨 DAO 验收覆盖共同提交、提交前隔离、重开后投影和统计一致，以及末端日志失败后的整体回滚。临时库一万条物品查询已有本机观察数据，不能外推为全部筛选、冷启动和 GUI 性能验收。')
    paragraph(doc, '当前 JAR 无 Main-Class 和内嵌 JDBC，不是可双击运行的最终桌面交付物。数据库版本识别不是自动迁移；旧手工库迁移、正式发布包、真实文件系统故障与 Excel界面等仍需后续验证。')
    heading(doc, '12.1 小组交接事项', level=2)
    paragraph(doc, '成员2接入Service时确认所有新增连接重载、身份与资格校验、固定错误码和事务范围。成员3通过Service调用界面，管理后台任务、分页、工具关闭和通用错误提示，不直接展示User认证字段。统计业务及公共工具接入的职责交叉仍需共同明确。')
    paragraph(doc, '分类停用字段与被引用分类删除策略、所有者查看违规下架原因的授权/对象关联、SessionManager及报表对象仍有设计待办。本说明书不构成小组三方已确认的接口或评审结论。')

    heading(doc, '附录A 全部 DAO 方法签名', True)
    paragraph(doc, '以下由当前接口源清单提取，包含普通与 Connection 首参数重载，共112个。方法签名是调用契约；业务语义仍按正文与对应模块说明。StatDao虽未显式声明非受检异常，也可能抛DataAccessException。')
    for name, block in api_sections:
        heading(doc, name, level=2)
        code(doc, block, 9)
    heading(doc, '附录B 图册与实现资料', True)
    table(doc, ['资料', '仓库路径或说明'], [
        ['迭代二完整图册', 'docs/design/iteration2/README.md'], ['接口与字段清单', 'docs/design/iteration2/dao-api.md；entity-fields.md'],
        ['接口变更与交接', 'docs/design/iteration2/interface-changes.md'], ['各模块实现说明', 'docs/development/iteration2-module1.md 至 module14.md'],
        ['数据层验收记录', 'docs/development/iteration2-data-acceptance.md'], ['Git检查点', 'docs/development/git-checkpoints.md'],
        ['初始化 SQL', '初始化脚本/04_数据库建表脚本_schema.sql；05_初始化数据脚本_init_data.sql'], ['构建与复现', '仓库根目录 pom.xml；本文件可由 scripts/build-iteration2-design.py 重建']], [4, 13])
    paragraph(doc, '本版简化图只为纸面阅读；完整图册含3张类图、6张接入时序图和1张状态图，并提供SVG/PNG、Mermaid/DOT或时序JSON源。黄色虚线参与者表示尚待实现的Service/GUI。')
    paragraph(doc, '本地提交节奏按用户要求每两个模块一次。模块一至十二检查点为1b3ae6f；模块十三与十四形成下一本地检查点，不自动推送GitHub。实际最新提交哈希以Git历史为准。')
    paragraph(doc, '资料依据：迭代一需求规格说明书、系统设计说明书、小组分工文档；当前Java与SQL源；模块一至十三说明及图册。原Word和PNG保留原样。')
    DEST.parent.mkdir(parents=True, exist_ok=True)
    doc.save(DEST)
    manifest = {'output': str(DEST.relative_to(ROOT)), 'sourceCommit': '1b3ae6f', 'daoMethods': 112, 'baselineTests': 253,
                'paragraphs': len(doc.paragraphs), 'tables': len(doc.tables), 'inlineFigures': len(doc.inline_shapes)}
    (BUILD / 'document-structure.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=True))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--figures-only', action='store_true')
    parser.add_argument('--node'); parser.add_argument('--modules-dir'); args = parser.parse_args()
    if args.figures_only:
        make_figures()
        if args.node and args.modules_dir:
            js = "const fs=require('node:fs'),p=require('node:path'),s=require(p.join(process.argv[1],'sharp')); const dir=process.argv[2]; Promise.all(fs.readdirSync(dir).filter(n=>n.endsWith('.svg')).map(n=>s(fs.readFileSync(p.join(dir,n)),{density:144}).png().toFile(p.join(dir,n.replace('.svg','.png'))))).then(()=>console.log('Word figures rasterized')).catch(e=>{console.error(e);process.exitCode=1})"
            subprocess.run([args.node, '-e', js, args.modules_dir, str(FIGURES)], check=True)
    else: build()
