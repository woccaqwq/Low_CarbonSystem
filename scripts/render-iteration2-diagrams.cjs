/* Documentation-only generator. No application code or databases are modified. */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'docs/design/iteration2');
const javaRoot = path.join(root, 'src/main/java/com/campus/recycle');
const checkOnly = process.argv.includes('--check');
const option = process.argv.indexOf('--modules-dir');
const modulesDir = option < 0 ? null : process.argv[option + 1];
const read = file => fs.readFileSync(path.join(javaRoot, file), 'utf8');
const stripComments = text => text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const xml = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const q = value => JSON.stringify(String(value));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const artifactHashes = {};
function save(name, text) {
  const file = path.join(output, name);
  const bytes = typeof text === 'string' ? Buffer.from(text, 'utf8') : text;
  artifactHashes[name] = hash(bytes);
  if (checkOnly) {
    if (!fs.existsSync(file) || !fs.readFileSync(file).equals(bytes)) throw new Error(`Stale generated source: ${name}`);
  } else fs.writeFileSync(file, bytes);
}
function fields(file) {
  return [...stripComments(read(file)).matchAll(/^ {4}private (?!static\b)(final )?([\w.$<>?, \[\]]+?) (\w+)(?:\s*=[^;]*)?;/gm)]
    .map(match => ({ type: match[2].trim(), name: match[3], immutable: Boolean(match[1]) }));
}
function methods(file) {
  const source = stripComments(read(file));
  const body = source.slice(source.indexOf('{') + 1, source.lastIndexOf('}'));
  return body.split(';').map(part => part.replace(/\s+/g, ' ').trim()).filter(Boolean).map(signature => {
    const match = signature.match(/^(?:public )?(.+?)\s+(\w+)\((.*?)\)(?: throws (.+))?$/);
    if (!match) throw new Error(`Unsupported interface signature: ${file}: ${signature}`);
    const params = match[3] ? match[3].split(',').map(param => param.trim()) : [];
    return { signature, returns: match[1], name: match[2], params, external: /^Connection\s+/.test(params[0] || '') };
  });
}
const methodKey = m => `${m.returns} ${m.name}(${(m.external ? m.params.slice(1) : m.params).map(p => p.replace(/\s+\w+$/, '')).join(',')})`;
const daoNames = fs.readdirSync(path.join(javaRoot, 'dao')).filter(file => file.endsWith('Dao.java')).sort();
const daos = daoNames.map(file => {
  const api = methods('dao/' + file);
  const ordinary = api.filter(m => !m.external), external = api.filter(m => m.external);
  if (ordinary.length !== external.length || ordinary.some(m => !external.some(c => methodKey(c) === methodKey(m)))) {
    throw new Error(`Unpaired Connection overloads: ${file}`);
  }
  const name = file.slice(0, -5), implFile = 'dao/impl/' + name + 'Impl.java';
  const implementation = stripComments(read(implFile));
  const mapping = implementation.match(/class\s+(\w+)\s+extends\s+BaseDao<([^>]+)>\s+implements\s+(\w+)/);
  if (!mapping || mapping[1] !== name + 'Impl' || mapping[3] !== name) throw new Error('Unexpected DAO mapping: ' + name);
  const getter = stripComments(read('dao/factory/DaoFactory.java')).match(new RegExp(`public static ${name} (\\w+)\\(`));
  if (!getter) throw new Error('Missing factory getter: ' + name);
  return { name, file: 'dao/' + file, implFile, rowType: mapping[2], getter: getter[1], api, ordinary, external };
});
function validateNode(node) {
  const source = stripComments(read(node.file));
  if (!new RegExp(`\\b(class|interface|enum)\\s+${node.id}\\b`).test(source)) throw new Error(`Missing type: ${node.id}`);
  for (const method of node.methods) {
    if (!new RegExp(`\\b${method}\\s*\\(`).test(source)) throw new Error(`Missing method ${node.id}.${method}`);
  }
}
function nodeMethods(node) {
  if (node.tag === 'interface' && node.file.startsWith('dao/')) return [...new Set(methods(node.file).filter(m => !m.external).map(m => m.name))];
  return node.methods;
}
function classDot(diagram) {
  const lines = ['digraph UML {', 'graph [rankdir=TB, bgcolor="white", pad="0.35", nodesep="0.55", ranksep="0.8", splines=polyline, overlap=false];',
    'node [shape=plain, fontname="Microsoft YaHei", fontsize=11];', 'edge [fontname="Microsoft YaHei", fontsize=9, color="#64748b", arrowsize=0.7];',
    'fontname="Microsoft YaHei"; fontsize=16;'];
  for (const node of diagram.nodes) {
    validateNode(node);
    const nodeFields = node.tag.startsWith('implementation') ? [] : fields(node.file);
    const nodeApi = nodeMethods(node);
    const limit = node.id === 'ItemQuery' ? 12 : 20;
    const fieldText = nodeFields.slice(0, limit).map(field => `- ${xml(field.name)} : ${xml(field.type)}${field.immutable ? ' {final}' : ''}`).join('<BR ALIGN="LEFT"/>');
    const apiText = nodeApi.map(method => `${node.id === 'BaseDao' ? '#' : '+'} ${xml(method)}(...)`).join('<BR ALIGN="LEFT"/>');
    const header = (node.tag ? `&lt;&lt;${xml(node.tag)}&gt;&gt;<BR/>` : '') + `<B>${xml(node.id)}</B>`;
    const widths = nodeFields.map(f => (`- ${f.name} : ${f.type}${f.immutable ? ' {final}' : ''}`).length * 6.7 + 26);
    const width = Math.ceil(Math.max(190, ...widths, (node.id.length + 4) * 7.6, node.tag.length * 7.2 + 24));
    const rows = [`<TR><TD WIDTH="${width - 2}" BGCOLOR="#dce9f8" CELLPADDING="8">${header}</TD></TR>`];
    if (fieldText) rows.push(`<TR><TD WIDTH="${width - 2}" ALIGN="LEFT" CELLPADDING="7">${fieldText}<BR ALIGN="LEFT"/></TD></TR>`);
    if (apiText) rows.push(`<TR><TD WIDTH="${width - 2}" ALIGN="LEFT" CELLPADDING="7">${apiText}<BR ALIGN="LEFT"/></TD></TR>`);
    lines.push(`${node.id} [label=<<TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" WIDTH="${width}" COLOR="#315b87">${rows.join('')}</TABLE>>];`);
  }
  for (const [from, to, label, tail, head, kind] of diagram.relations) {
    if (!diagram.nodes.some(n => n.id === from) || !diagram.nodes.some(n => n.id === to)) throw new Error(`Unknown relation ${from} -> ${to}`);
    const style = kind === 'implements' ? 'style=dashed,arrowhead=empty' : kind === 'extends' ? 'arrowhead=empty' :
      kind === 'dependency' ? 'style=dashed,arrowhead=vee' : kind === 'composition' ? 'dir=both,arrowtail=diamond,arrowhead=none' : 'arrowhead=none';
    lines.push(`${from} -> ${to} [xlabel=${q(label)},taillabel=${q(tail)},headlabel=${q(head)},constraint=false,${style}];`);
  }
  for (const row of diagram.rows || []) {
    lines.push(`{rank=same; ${row.join(';')};}`);
    for (let i = 1; i < row.length; i++) lines.push(`${row[i - 1]} -> ${row[i]} [style=invis,weight=100];`);
  }
  for (let i = 1; i < (diagram.rows || []).length; i++) lines.push(`${diagram.rows[i - 1][0]} -> ${diagram.rows[i][0]} [style=invis,weight=100,minlen=2];`);
  lines.push('}');
  return lines.join('\n') + '\n';
}
function classMermaid(diagram) {
  const lines = ['classDiagram'];
  for (const node of diagram.nodes) {
    lines.push(`  class ${node.id} {`);
    if (node.tag === 'interface') lines.push('    <<interface>>');
    if (node.tag === 'enum') lines.push('    <<enumeration>>');
    for (const field of fields(node.file)) {
      if (node.tag.startsWith('implementation')) break;
      const type = field.type.replaceAll('<', '~').replaceAll('>', '~').replaceAll(' ', '');
      lines.push(`    -${type} ${field.name}`);
    }
    for (const method of nodeMethods(node)) lines.push(`    ${node.id === 'BaseDao' ? '#' : '+'}${method}(...)`);
    lines.push('  }');
  }
  for (const [from, to, label, tail, head, kind] of diagram.relations) {
    const arrow = kind === 'implements' ? '..|>' : kind === 'extends' ? '--|>' : kind === 'dependency' ? '..>' : kind === 'composition' ? '*--' : '--';
    lines.push(`  ${from}${tail ? ' "' + tail + '"' : ''} ${arrow}${head ? ' "' + head + '"' : ''} ${to} : ${label.replaceAll('<', '').replaceAll('>', '')}`);
  }
  return lines.join('\n') + '\n';
}
function wrap(text, width) {
  const lines = []; let line = '', used = 0;
  for (const char of String(text)) {
    const size = char.codePointAt(0) > 255 ? 1.85 : 1;
    if (char === '\n' || used + size > width) { lines.push(line); line = ''; used = 0; if (char === '\n') continue; }
    line += char; used += size;
  }
  if (line || !lines.length) lines.push(line);
  return lines;
}
function sequenceMermaid(diagram) {
  const lines = ['sequenceDiagram', '  autonumber'];
  for (const participant of diagram.participants) lines.push(`  participant ${participant.id} as ${participant.label}`);
  let depth = 0;
  for (const event of diagram.events) {
    if (event.kind === 'message') lines.push(`  ${event.from}${event.reply ? '-->>' : '->>'}${event.to}: ${event.text}`);
    else if (event.kind === 'note') lines.push(`  Note over ${diagram.participants[0].id},${diagram.participants.at(-1).id}: ${event.text}`);
    else if (['alt', 'opt', 'loop'].includes(event.kind)) { depth++; lines.push(`  ${event.kind} ${event.text}`); }
    else if (event.kind === 'else') { if (!depth) throw new Error('Unmatched else'); lines.push(`  else ${event.text}`); }
    else if (event.kind === 'end') { if (!depth--) throw new Error('Unmatched end'); lines.push('  end'); }
  }
  if (depth) throw new Error(`Unclosed fragment in ${diagram.id}`);
  return lines.join('\n') + '\n';
}
function framedClassSvg(svg, diagram) {
  const view = svg.match(/viewBox="([^"]+)"/)[1].split(/\s+/).map(Number);
  const inner = svg.slice(svg.indexOf('>', svg.indexOf('<svg')) + 1, svg.lastIndexOf('</svg>'));
  const width = Math.ceil(view[2] + 40);
  const notes = wrap(diagram.note, (width - 72) / 7);
  const footerY = view[3] + 126, height = footerY + notes.length * 21 + 30;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="${width}" height="${height}" fill="white"/><style>text{font-family:"Microsoft YaHei","Noto Sans CJK SC",sans-serif}</style><text x="25" y="34" font-size="23" font-weight="bold">${xml(diagram.title)}</text><text x="25" y="63" font-size="12">${xml(diagram.subtitle)}</text><g transform="translate(20 90)">${inner}</g><rect x="20" y="${footerY - 16}" width="${width - 40}" height="${notes.length * 21 + 18}" fill="#fff8dc" stroke="#d1ad4b"/>${notes.map((line, i) => `<text x="30" y="${footerY + 2 + i * 21}" font-size="12">${xml(line)}</text>`).join('')}</svg>\n`;
}
function sequenceSvg(diagram) {
  const column = 210, left = 125, width = left * 2 + column * (diagram.participants.length - 1);
  const positions = Object.fromEntries(diagram.participants.map((participant, i) => [participant.id, left + i * column]));
  const laidOut = []; let y = 210, number = 0;
  for (const event of diagram.events) {
    let available = (width - 90) / 7.2;
    if (event.kind === 'message') {
      if (!(event.from in positions) || !(event.to in positions)) throw new Error(`Unknown participant: ${diagram.id}`);
      available = Math.max(22, (Math.abs(positions[event.from] - positions[event.to]) || column) / 7.2 - 4);
    }
    const lines = wrap(event.text || '', available);
    const height = event.kind === 'end' ? 20 : Math.max(36, lines.length * 18 + (event.kind === 'message' ? 28 : 18));
    laidOut.push({ event, y, height, lines, number: event.kind === 'message' ? ++number : null });
    y += height;
  }
  const height = y + 70;
  const svg = [`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">`,
    '<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8 Z" fill="#294a70"/></marker></defs>',
    `<rect width="${width}" height="${height}" fill="white"/>`,
    '<style>text{font-family:"Microsoft YaHei","Noto Sans CJK SC",sans-serif;fill:#18314b} .body{font-size:12px} .small{font-size:11px}</style>',
    `<text x="30" y="36" font-size="24" font-weight="bold">${xml(diagram.title)}</text>`,
    `<text x="30" y="65" font-size="13">${xml(diagram.subtitle)}</text>`,
    '<text x="30" y="91" class="small">蓝色：已有类型/能力　黄色虚线：待实现接入组件　alt / else：互斥路径　同一 conn：同一数据库事务</text>'];
  const fragments = [];
  for (const row of laidOut) {
    if (['alt', 'opt', 'loop'].includes(row.event.kind)) fragments.push(row);
    if (row.event.kind === 'end') {
      const start = fragments.pop();
      if (start) svg.push(`<rect x="16" y="${start.y}" width="${width - 32}" height="${row.y + row.height - start.y}" fill="none" stroke="#9aaec3" stroke-width="1.1"/>`);
    }
  }
  for (const participant of diagram.participants) {
    const x = positions[participant.id];
    svg.push(`<line x1="${x}" y1="179" x2="${x}" y2="${height - 30}" stroke="#a8b6c5" stroke-dasharray="6 5"/>`,
      `<rect x="${x - 96}" y="123" width="192" height="56" rx="4" fill="${participant.planned ? '#fff3d3' : '#e5effb'}" stroke="#315b87" ${participant.planned ? 'stroke-dasharray="5 3"' : ''}/>`);
    const labels = wrap(participant.label, 24);
    labels.forEach((label, index) => svg.push(`<text x="${x}" y="${145 + index * 17}" text-anchor="middle" font-size="12" font-weight="bold">${xml(label)}</text>`));
  }
  for (const row of laidOut) {
    const { event, lines, y: top } = row;
    if (event.kind === 'message') {
      const x1 = positions[event.from], x2 = positions[event.to];
      const textX = Math.min(x1, x2) + 9;
      lines.forEach((label, index) => svg.push(`<text class="body" x="${textX}" y="${top + 15 + index * 18}">${xml((index === 0 ? row.number + '. ' : '') + label)}</text>`));
      const arrowY = top + lines.length * 18 + 7;
      if (x1 === x2) svg.push(`<path d="M${x1} ${arrowY} h65 v12 h-65" fill="none" stroke="#294a70" marker-end="url(#arrow)"/>`);
      else svg.push(`<line x1="${x1}" y1="${arrowY}" x2="${x2}" y2="${arrowY}" stroke="#294a70" ${event.reply ? 'stroke-dasharray="6 4"' : ''} marker-end="url(#arrow)"/>`);
    } else if (event.kind !== 'end') {
      const note = event.kind === 'note';
      svg.push(`<rect x="${note ? 27 : 17}" y="${top + 2}" width="${width - (note ? 54 : 34)}" height="${row.height - 5}" fill="${note ? '#fff8df' : '#eef3f8'}" stroke="${note ? '#d7b85f' : '#c5d0dc'}"/>`);
      lines.forEach((label, index) => svg.push(`<text class="body" x="38" y="${top + 19 + index * 18}">${xml((index === 0 && !note ? event.kind + '  ' : '') + label)}</text>`));
    }
  }
  return svg.join('\n') + '\n</svg>\n';
}
function inventory() {
  const total = daos.reduce((sum, dao) => sum + dao.api.length, 0);
  if (daos.length !== 9 || total !== 112) throw new Error(`Unexpected DAO inventory: ${daos.length}/${total}`);
  let text = '# 迭代二 DAO 接口清单\n\n由当前 Java 接口源自动生成，按类型与参数完整保留方法签名，不表示新增 Service 契约。\n九个 DAO 共 56 个普通方法和 56 个 Connection 首参数重载，合计 112 个。\n\n| 接口 | 实现类 | BaseDao 映射类型 | 普通 | Connection | 工厂方法 |\n| --- | --- | --- | ---: | ---: | --- |\n';
  for (const dao of daos) text += `| ${dao.name} | ${dao.name}Impl | ${dao.rowType} | ${dao.ordinary.length} | ${dao.external.length} | ${dao.getter}() |\n`;
  for (const dao of daos) text += `\n## ${dao.name}\n\n[接口源](../../../src/main/java/com/campus/recycle/${dao.file})\n\n\x60\x60\x60java\n${dao.api.map(method => method.signature + ';').join('\n')}\n\x60\x60\x60\n`;
  text += '\nStatDao 的 DataAccessException 为非受检异常，源签名未显式声明 throws，仍可能抛出。其他签名按源文件原样保留。\n';
  save('dao-api.md', text);
  const entities = fs.readdirSync(path.join(javaRoot, 'entity')).filter(file => file.endsWith('.java')).sort();
  let values = '# 迭代二实体字段清单\n\n从 Java 顶层 private 实例字段生成，排除静态常量和 Builder 内部字段。final 是字段不可重新赋值，不代表所引用的元素深度不可变。\n';
  for (const file of entities) {
    values += `\n## ${file.slice(0, -5)}\n\n[类型源](../../../src/main/java/com/campus/recycle/entity/${file})\n\n| 字段 | Java 类型 | final |\n| --- | --- | --- |\n`;
    for (const field of fields('entity/' + file)) values += `| ${field.name} | ${field.type.replaceAll('|', '\\|')} | ${field.immutable ? '是' : '否'} |\n`;
  }
  save('entity-fields.md', values);
}
async function main() {
  const modelFile = path.join(output, 'diagram-model.mjs');
  const { classDiagrams, sequences } = await import(pathToFileURL(modelFile));
  inventory();
  let viz, sharp;
  if (!checkOnly) {
    const load = name => modulesDir ? require(path.join(modulesDir, name)) : require(name);
    viz = await load('@viz-js/viz').instance(); sharp = load('sharp');
  }
  const manifest = [];
  for (const diagram of classDiagrams) {
    const dot = classDot(diagram); save(diagram.id + '.dot', dot); save(diagram.id + '.mmd', classMermaid(diagram));
    if (!checkOnly) {
      const svg = framedClassSvg(viz.renderString(dot, { format: 'svg' }), diagram);
      fs.writeFileSync(path.join(output, diagram.id + '.svg'), svg);
      await sharp(Buffer.from(svg)).resize({ width: 2200, withoutEnlargement: true }).png().toFile(path.join(output, diagram.id + '.png'));
    }
    manifest.push({ id: diagram.id, type: 'class', title: diagram.title });
  }
  for (const diagram of sequences) {
    save(diagram.id + '.sequence.json', JSON.stringify(diagram, null, 2) + '\n');
    save(diagram.id + '.mmd', sequenceMermaid(diagram));
    const svg = sequenceSvg(diagram); save(diagram.id + '.svg', svg);
    if (!checkOnly) await sharp(Buffer.from(svg)).png().toFile(path.join(output, diagram.id + '.png'));
    manifest.push({ id: diagram.id, type: 'sequence', title: diagram.title });
  }
  const state = 'stateDiagram-v2\n  state ItemStatus {\n    ON_SALE --> OFF_SHELF: offline()\n    OFF_SHELF --> ON_SALE: publish()\n    ON_SALE --> FLOWED: markFlowed()\n  }\n  state FlowStatus {\n    PENDING --> DEAL: confirm()\n    PENDING --> CANCELLED: cancel(reason)\n  }\n';
  save('10-state.mmd', state);
  const stateDot = 'digraph states { graph [rankdir=LR,bgcolor="white",pad="0.4"]; node [shape=box,style="rounded,filled",fillcolor="#e5effb",fontname="Microsoft YaHei"]; edge [fontname="Microsoft YaHei"]; subgraph cluster_item { label="ItemStatus (entity guards)"; ON_SALE -> OFF_SHELF [label="offline()"]; OFF_SHELF -> ON_SALE [label="publish()"]; ON_SALE -> FLOWED [label="markFlowed()"]; } subgraph cluster_flow { label="FlowStatus"; PENDING -> DEAL [label="confirm()"]; PENDING -> CANCELLED [label="cancel(reason)"]; } FLOWED [peripheries=2]; DEAL [peripheries=2]; CANCELLED [peripheries=2]; }\n';
  save('10-state.dot', stateDot);
  if (!checkOnly) {
    const svg = viz.renderString(stateDot, { format: 'svg' }); fs.writeFileSync(path.join(output, '10-state.svg'), svg);
    await sharp(Buffer.from(svg)).png().toFile(path.join(output, '10-state.png'));
  }
  manifest.push({ id: '10-state', type: 'state', title: '物品与流转状态图' });
  for (const entry of manifest) for (const suffix of ['.svg', '.png']) if (!fs.existsSync(path.join(output, entry.id + suffix))) throw new Error('Missing preview: ' + entry.id + suffix);
  const inputs = [...new Set(classDiagrams.flatMap(diagram => diagram.nodes.map(node => node.file)).concat(daos.flatMap(dao => [dao.file, dao.implFile])))].sort();
  const sourceHash = hash(inputs.map(file => file + '\n' + read(file)).join('\n') + fs.readFileSync(modelFile, 'utf8'));
  const sourceCommit = execFileSync('git', ['log', '-1', '--format=%h', '--', 'src/main/java'], { cwd: root, encoding: 'utf8' }).trim();
  save('manifest.json', JSON.stringify({ sourceCommit, sourceHash, daoCount: 9, daoMethodCount: 112, diagrams: manifest, generatedTextHashes: { ...artifactHashes } }, null, 2) + '\n');
  process.stdout.write(`${checkOnly ? 'CHECK' : 'RENDER'} PASS: ${manifest.length} diagrams, 9 DAOs / 112 methods, ${inputs.length} source types\n`);
}
main().catch(error => { process.stderr.write(error.stack + '\n'); process.exitCode = 1; });
