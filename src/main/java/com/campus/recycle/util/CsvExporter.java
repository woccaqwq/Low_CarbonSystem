package com.campus.recycle.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** 通用 UTF-8 BOM CSV 导出，无业务层依赖；数值保持数字，危险前缀文本添加 TAB。 */
public final class CsvExporter {
    /** 创建无状态工具，可注入后续 Service。 */
    public CsvExporter() { }

    /**
     * 流式写入同目录临时文件，全部成功后移动到新目标，不覆盖现有文件。
     * @param target 目标文件，父目录须已存在
     * @param headers 非空表头，所有单元须为非 null String
     * @param rows 等宽行，单元支持 null、String、Boolean 及文档列明的 JDK 数字类型
     * @return 目标的绝对规范路径
     * @throws IllegalArgumentException 参数、行宽、单元类型或数值不合法
     * @throws IOException 编码、文件写入或发布失败；写入完成前不发布目标文件
     */
    public Path export(Path target, List<String> headers, Iterable<? extends List<?>> rows) throws IOException {
        if (target == null || headers == null || headers.isEmpty() || rows == null) {
            throw new IllegalArgumentException("目标、表头和数据不能为空，表头至少一列");
        }
        List<String> encodedHeaders = new ArrayList<>(headers.size());
        for (Object header : headers) {
            if (!(header instanceof String text)) { throw new IllegalArgumentException("表头必须是非 null 文本"); }
            encodedHeaders.add(encodeText(text));
        }
        Path destination = target.toAbsolutePath().normalize();
        if (destination.getFileName() == null) { throw new IllegalArgumentException("目标必须为文件路径"); }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(destination.toString());
        }
        Path staged = null;
        try {
            staged = Files.createTempFile(destination.getParent(), ".csv-export-", ".tmp");
            write(staged, encodedHeaders, rows);
            // 不使用 ATOMIC_MOVE：该选项对已存在目标的覆盖行为依赖平台。
            return Files.move(staged, destination);
        } catch (IOException | RuntimeException | Error failure) {
            if (staged != null) {
                try { Files.deleteIfExists(staged); }
                catch (IOException | RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        }
    }

    private static void write(Path staged, List<String> headers, Iterable<? extends List<?>> rows) throws IOException {
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (OutputStream output = Files.newOutputStream(staged, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             Writer writer = new BufferedWriter(new OutputStreamWriter(output, encoder))) {
            writer.write('\uFEFF');
            writeRecord(writer, headers);
            for (List<?> row : rows) {
                if (row == null) { throw new IllegalArgumentException("数据行不能为 null"); }
                List<?> snapshot = new ArrayList<>(row);
                if (snapshot.size() != headers.size()) { throw new IllegalArgumentException("数据行列数必须与表头一致"); }
                List<String> encoded = new ArrayList<>(snapshot.size());
                for (Object cell : snapshot) { encoded.add(encodeCell(cell)); }
                writeRecord(writer, encoded);
            }
        }
    }

    private static void writeRecord(Writer writer, List<String> encoded) throws IOException {
        for (int index = 0; index < encoded.size(); index++) {
            if (index != 0) { writer.write(','); }
            writer.write(encoded.get(index));
        }
        writer.write("\r\n");
    }

    private static String encodeCell(Object cell) {
        if (cell == null) { return ""; }
        if (cell instanceof String text) { return encodeText(text); }
        if (cell instanceof Boolean flag) { return flag.toString(); }
        Class<?> type = cell.getClass();
        if (type == BigDecimal.class) { return ((BigDecimal) cell).toPlainString(); }
        if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == BigInteger.class) { return cell.toString(); }
        if (type == Float.class && Float.isFinite((Float) cell)
                || type == Double.class && Double.isFinite((Double) cell)) { return cell.toString(); }
        throw new IllegalArgumentException("单元必须是文本、布尔值或受支持的有限数字");
    }

    private static String encodeText(String text) {
        if (text.indexOf('\0') >= 0) { throw new IllegalArgumentException("CSV 文本不能包含 NUL 字符"); }
        String protectedText = hasFormulaPrefix(text) ? "\t" + text : text;
        return "\"" + protectedText.replace("\"", "\"\"") + "\"";
    }

    private static boolean hasFormulaPrefix(String text) {
        if (!text.isEmpty() && (text.charAt(0) == '\t' || text.charAt(0) == '\r' || text.charAt(0) == '\n')) {
            return true;
        }
        for (int offset = 0; offset < text.length();) {
            int character = text.codePointAt(offset);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)
                    && Character.getType(character) != Character.FORMAT) {
                return character == '=' || character == '+' || character == '-' || character == '@'
                        || character == '＝' || character == '＋' || character == '－' || character == '＠';
            }
            offset += Character.charCount(character);
        }
        return false;
    }
}
