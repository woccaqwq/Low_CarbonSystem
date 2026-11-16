package com.campus.recycle.util;

import com.campus.recycle.dao.impl.CategoryDaoImpl;
import com.campus.recycle.dao.impl.ItemDaoImpl;
import com.campus.recycle.dao.impl.StatDaoImpl;
import com.campus.recycle.entity.Category;
import com.campus.recycle.entity.CategoryStat;
import com.campus.recycle.entity.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @TempDir Path directory;
    private final CsvExporter exporter = new CsvExporter();

    @Test
    void writesBomChineseHeadersNumericColumnsAndCrLf() throws Exception {
        Path target = directory.resolve("中文报表.csv");
        Path result = exporter.export(target, List.of("分类", "数量", "系数"),
                List.of(List.of("教材", 3L, new BigDecimal("2.40"))));
        assertEquals(target.toAbsolutePath().normalize(), result);
        byte[] bytes = Files.readAllBytes(target);
        assertArrayEquals(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, Arrays.copyOf(bytes, 3));
        assertEquals("\uFEFF\"分类\",\"数量\",\"系数\"\r\n\"教材\",3,2.40\r\n", read(target));
    }

    @Test
    void quotesCommasDoubleQuotesAndEmbeddedLineBreaks() throws Exception {
        Path target = directory.resolve("escaped.csv");
        exporter.export(target, List.of("分,类", "备\"注"), List.of(List.of("甲,乙\"丙", "第一行\r\n第二行\n末行")));
        assertEquals("\uFEFF\"分,类\",\"备\"\"注\"\r\n\"甲,乙\"\"丙\",\"第一行\r\n第二行\n末行\"\r\n", read(target));
    }

    @Test
    void handlesNullEmptyBooleanAndSupplementaryUnicode() throws Exception {
        Path target = directory.resolve("values.csv");
        exporter.export(target, List.of("a", "b", "c", "d"),
                List.of(Arrays.asList(null, "", true, "校园🌱")));
        assertEquals("\uFEFF\"a\",\"b\",\"c\",\"d\"\r\n,\"\",true,\"校园🌱\"\r\n", read(target));
    }

    @Test
    void writesHeaderOnlyForEmptyData() throws Exception {
        Path target = directory.resolve("empty.csv");
        exporter.export(target, List.of("分类", "数量"), List.of());
        assertEquals("\uFEFF\"分类\",\"数量\"\r\n", read(target));
    }

    @Test
    void emitsSupportedNumbersWithoutLocaleGroupingOrFormulaTextPrefix() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Path target = directory.resolve("numbers.csv");
            exporter.export(target, List.of("a", "b", "c", "d", "e", "f", "g", "h"),
                    List.of(List.of((byte) 1, (short) 2, -3, 4L, new BigInteger("123456789012345678901"),
                            new BigDecimal("1E+3"), 1.25f, -2.5d)));
            assertEquals("\uFEFF\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\"\r\n"
                    + "1,2,-3,4,123456789012345678901,1000,1.25,-2.5\r\n", read(target));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void prefixesRiskyTextIncludingHeadersInsideQuotedCells() throws Exception {
        List<String> texts = List.of("=1+2", "+SUM(A1)", "-2.5", "@name", "  =1", "\t=1", "\r=1", "\n=1",
                "＝1", "＋1", "－1", "＠name", "\uFEFF=1");
        for (int index = 0; index < texts.size(); index++) {
            Path target = directory.resolve("formula-" + index + ".csv");
            String text = texts.get(index);
            exporter.export(target, List.of(text), List.of(List.of(text)));
            String field = "\"\t" + text + "\"\r\n";
            assertEquals("\uFEFF" + field + field, read(target));
        }
    }

    @Test
    void keepsFormulaLikeTextInMiddleOfProperlyEscapedCell() throws Exception {
        Path target = directory.resolve("middle.csv");
        exporter.export(target, List.of("文本"), List.of(List.of("普通,=1+2;\"@x")));
        assertEquals("\uFEFF\"文本\"\r\n\"普通,=1+2;\"\"@x\"\r\n", read(target));
    }

    @Test
    void rejectsInvalidStructureBeforePublishingTarget() throws Exception {
        Path target = directory.resolve("invalid.csv");
        assertThrows(IllegalArgumentException.class, () -> exporter.export(null, List.of("h"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, Arrays.asList("h", null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, List.of("h"), null));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, List.of("h"), Arrays.asList((List<?>) null)));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, List.of("h"), List.of(List.of(1, 2))));
        assertThrows(IllegalArgumentException.class, () -> exporter.export(target, List.of("h"), List.of(List.of())));
        assertNoFiles();
    }

    @Test
    void rejectsNonFiniteAndUnsupportedNumbersOrObjects() throws Exception {
        List<Object> invalid = List.of(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                new AtomicInteger(3), new Object());
        for (Object cell : invalid) {
            assertThrows(IllegalArgumentException.class, () -> exporter.export(directory.resolve("invalid.csv"),
                    List.of("h"), List.of(List.of(cell))));
            assertNoFiles();
        }
    }

    @Test
    void rejectsNulAndMalformedUnicodeWithoutReplacementOrPartialFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> exporter.export(directory.resolve("bad.csv"),
                List.of("h"), List.of(List.of("bad\u0000text"))));
        assertNoFiles();
        for (String text : List.of("bad\uD800", "\uDC00")) {
            assertThrows(java.nio.charset.MalformedInputException.class, () -> exporter.export(directory.resolve("bad.csv"),
                    List.of("h"), List.of(List.of("valid"), List.of(text))));
            assertNoFiles();
        }
    }

    @Test
    void refusesExistingTargetAndPreservesItsContents() throws Exception {
        Path target = directory.resolve("existing.csv");
        Files.writeString(target, "已有内容", StandardCharsets.UTF_8);
        assertThrows(FileAlreadyExistsException.class,
                () -> exporter.export(target, List.of("h"), List.of(List.of(1))));
        assertEquals("已有内容", read(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void preservesTargetCreatedWhileDataIsBeingProduced() throws Exception {
        Path target = directory.resolve("race.csv");
        Iterable<List<?>> rows = () -> {
            try { Files.writeString(target, "其他操作创建"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            return List.<List<?>>of().iterator();
        };
        assertThrows(FileAlreadyExistsException.class, () -> exporter.export(target, List.of("h"), rows));
        assertEquals("其他操作创建", read(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void leavesNoTemporaryFileWhenIterationFailsAfterWritingSomeRows() throws Exception {
        IllegalStateException failure = new IllegalStateException("fixture iterator failure");
        Iterable<List<?>> rows = () -> new Iterator<>() {
            private boolean emitted;
            public boolean hasNext() { if (emitted) { throw failure; } return true; }
            public List<?> next() { emitted = true; return List.of("valid"); }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> exporter.export(directory.resolve("failed.csv"), List.of("h"), rows)));
        assertNoFiles();
    }

    @Test
    void doesNotCreateMissingParentsOrReplaceDirectories() throws Exception {
        Path missing = directory.resolve("missing");
        assertThrows(IOException.class, () -> exporter.export(missing.resolve("file.csv"), List.of("h"), List.of()));
        assertFalse(Files.exists(missing));
        Path folder = Files.createDirectory(directory.resolve("folder"));
        assertThrows(FileAlreadyExistsException.class, () -> exporter.export(folder, List.of("h"), List.of()));
        assertTrue(Files.isDirectory(folder));
    }

    @Test
    void consumesLargeInputOnceWithoutRequiringWholeTableInMemory() throws Exception {
        AtomicInteger iterations = new AtomicInteger();
        Iterable<List<?>> rows = () -> {
            assertEquals(1, iterations.incrementAndGet());
            return new Iterator<>() {
                private int next;
                public boolean hasNext() { return next < 10_000; }
                public List<?> next() {
                    if (!hasNext()) { throw new NoSuchElementException(); }
                    return List.of("物品" + next, next++);
                }
            };
        };
        Path target = directory.resolve("large.csv");
        exporter.export(target, List.of("名称", "编号"), rows);
        try (var lines = Files.lines(target, StandardCharsets.UTF_8)) { assertEquals(10_001, lines.count()); }
        assertTrue(read(target).endsWith("\"物品9999\",9999\r\n"));
    }

    @Test
    void acceptsCurrentStatDaoRowsWithoutUtilDependingOnBusinessTypes() throws Exception {
        DBUtil database = DBUtil.forPath(directory.resolve("stats.db"));
        int category = new CategoryDaoImpl(database).insert(new Category("文具,交换区", null, 100));
        Item item = new Item();
        item.setOwnerId(2);
        item.setCategoryId(category);
        item.setTitle("导出验收物品");
        new ItemDaoImpl(database).insert(item);
        CategoryStat stat = new StatDaoImpl(database).countByCategory().stream()
                .filter(row -> row.getCategoryId() != null && row.getCategoryId() == category).findFirst().orElseThrow();
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of(stat.getCategoryName(), stat.getCounts().getOnSaleItemCount(),
                stat.getCounts().getDealFlowCount()));
        Path target = directory.resolve("stats.csv");
        exporter.export(target, List.of("分类", "在架数", "成交数"), rows);
        assertEquals("\uFEFF\"分类\",\"在架数\",\"成交数\"\r\n\"文具,交换区\",1,0\r\n", read(target));
    }

    private static String read(Path target) throws IOException { return Files.readString(target, StandardCharsets.UTF_8); }
    private void assertNoFiles() throws IOException {
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
