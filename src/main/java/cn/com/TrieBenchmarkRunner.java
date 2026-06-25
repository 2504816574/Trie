package cn.com;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class TrieBenchmarkRunner {

	public static final String BASE_DIR = "./src/main/java/com/myutils/trie/";
	private static final String DB_URL = "jdbc:sqlite:trie_benchmark.db";
	private static final boolean SAVE_TO_FILE = false;
	private static final int SEARCH_COUNT = 10000;
	private static final int MUTATE_COUNT = 1000;
	private static int totalDataCount = 0;

	public static void main(String[] args) throws Exception {
		System.out.println("===============================================================");
		System.out.println("   TRIE 基准测试 (含增删改查)");
		System.out.println("===============================================================");

		File dbFile = new File("trie_benchmark.db");
		if (!dbFile.exists()) {
			System.err.println(" 未找到数据库，请先运行 TrieDataInitializer！");
			return;
		}

		// 读取总数据量
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM company_info")) {
			if (rs.next()) totalDataCount = rs.getInt(1);
		}
		System.out.println(">> 数据库总量: " + totalDataCount + " 条\n");

		long jvmBaseMem = gcAndGetMemory();
		System.out.println(">> JVM 基准内存: " + (jvmBaseMem / 1024 / 1024) + " MB\n");

		// 预取 CRUD 样本数据
		System.out.println(">> 预取增删改样本 " + MUTATE_COUNT + " 条...");
		List<String[]> mutateSamples = fetchSamples(MUTATE_COUNT);
		System.out.println(">> 增删改样本获取完成");
		System.out.println(">> 生成多样化查询样本 " + SEARCH_COUNT + " 条(含精确/片段/脏数据/多公司)...");
		List<String> searchQueries = generateSearchQueries(SEARCH_COUNT);
		System.out.println(">> 查询样本生成完成\n");

		// ===== 按需调整调用顺序 =====
		// 标准树：堆内 TrieNode 对象，正序入树（从左到右），分叉极多，内存消耗大
//		test1_std_unpart_normal(searchQueries, mutateSamples);
		// 标准树：反转入树（"有限公司"变根路径），利用工商后缀同质化合并节点，内存缩减约70%
//		test2_std_unpart_reverse(searchQueries, mutateSamples);
		// 标准树：10分区+正序，code%10 路由到10棵隔离树，分散读写锁竞争
//		test3_std_part_normal(searchQueries, mutateSamples);
		// 标准树：10分区+反序，堆内标准树终极形态（合并+并发隔离）
//		test4_std_part_reverse(searchQueries, mutateSamples);


		// 压缩前缀树：10分区+反序，最终生产形态（路径压缩+反序+分区并发）
		test8_comp_part_reverse(searchQueries, mutateSamples);
		// 压缩前缀树(Radix Tree)：路径压缩合并单子节点，正序入树，节点数远少于标准树
		test5_comp_unpart_normal(searchQueries, mutateSamples);
		// 压缩前缀树：反转入树，结合路径压缩+后缀同质化，节点数最少
		test6_comp_unpart_reverse(searchQueries, mutateSamples);
		// 压缩前缀树：10分区+正序，10棵隔离 Radix Tree，并发安全
		test7_comp_part_normal(searchQueries, mutateSamples);


		// 生成报表
		new File(BASE_DIR).mkdirs();
		String json = BenchmarkResult.resultsToJson();
		String jsonPath = BASE_DIR + "benchmark_report.json";
		try (FileWriter fw = new FileWriter(jsonPath)) { fw.write(json); }
		System.out.println("\n 报表已生成: " + jsonPath);
		System.out.println("请用浏览器打开: " + BASE_DIR + "benchmark_report.html");
	}

	// ========================================================================
	// 【1】标准树 - 不分区 - 正序
	// ========================================================================
	private static void test1_std_unpart_normal(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [1] 标准树 - 不分区 - 正序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		StandardTrie t = new StandardTrie();
		loadToSingleTrie(t, false);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchStdOne(t, searchQueries, false);

		long[] crud = benchmarkStdCrudOne(t, mutateSamples, false);

		BenchmarkResult.create("标准树-不分区-正序", "标准树 不分区 正序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【2】标准树 - 不分区 - 反序
	// ========================================================================
	private static void test2_std_unpart_reverse(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [2] 标准树 - 不分区 - 反序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		StandardTrie t = new StandardTrie();
		loadToSingleTrie(t, true);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchStdOne(t, searchQueries, true);

		long[] crud = benchmarkStdCrudOne(t, mutateSamples, true);

		BenchmarkResult.create("标准树-不分区-反序", "标准树 不分区 反序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【3】标准树 - 尾缀10分区 - 正序
	// ========================================================================
	private static void test3_std_part_normal(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [3] 标准树 - 尾缀10分区 - 正序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		StandardTrie[] t = new StandardTrie[10];
		for (int i = 0; i < 10; i++) t[i] = new StandardTrie();
		loadToPartitionedTries(t, false);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchStdPart(t, searchQueries, false);

		long[] crud = benchmarkStdCrudPart(t, mutateSamples, false);

		BenchmarkResult.create("标准树-10分区-正序", "标准树 10分区 正序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【4】标准树 - 尾缀10分区 - 反序
	// ========================================================================
	private static void test4_std_part_reverse(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [4] 标准树 - 尾缀10分区 - 反序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		StandardTrie[] t = new StandardTrie[10];
		for (int i = 0; i < 10; i++) t[i] = new StandardTrie();
		loadToPartitionedTries(t, true);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchStdPart(t, searchQueries, true);

		long[] crud = benchmarkStdCrudPart(t, mutateSamples, true);

		BenchmarkResult.create("标准树-10分区-反序", "标准树 10分区 反序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【5】压缩树 - 不分区 - 正序
	// ========================================================================
	private static void test5_comp_unpart_normal(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [5] 压缩树 - 不分区 - 正序");
		printMem();
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		CompressedTrie c = new CompressedTrie();
		loadToCompressedTrie(c, false);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchCompOne(c, searchQueries, false);

		long[] crud = benchmarkCompCrudOne(c, mutateSamples, false);

		BenchmarkResult.create("压缩树-不分区-正序", "压缩树 不分区 正序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【6】压缩树 - 不分区 - 反序
	// ========================================================================
	private static void test6_comp_unpart_reverse(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [6] 压缩树 - 不分区 - 反序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		CompressedTrie c = new CompressedTrie();
		loadToCompressedTrie(c, true);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchCompOne(c, searchQueries, true);

		long[] crud = benchmarkCompCrudOne(c, mutateSamples, true);

		BenchmarkResult.create("压缩树-不分区-反序", "压缩树 不分区 反序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【7】压缩树 - 尾缀10分区 - 正序
	// ========================================================================
	private static void test7_comp_part_normal(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [7] 压缩树 - 尾缀10分区 - 正序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		CompressedTrie[] c = new CompressedTrie[10];
		for (int i = 0; i < 10; i++) c[i] = new CompressedTrie();
		loadToPartitionedCompressedTries(c, false);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchCompPart(c, searchQueries, false);

		long[] crud = benchmarkCompCrudPart(c, mutateSamples, false);

		BenchmarkResult.create("压缩树-10分区-正序", "压缩树 10分区 正序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【8】压缩树 - 尾缀10分区 - 反序
	// ========================================================================
	private static void test8_comp_part_reverse(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [8] 压缩树 - 尾缀10分区 - 反序");
		printMem();
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		CompressedTrie[] c = new CompressedTrie[10];
		for (int i = 0; i < 10; i++) c[i] = new CompressedTrie();
		loadToPartitionedCompressedTries(c, true);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);

		long searchMs = benchmarkSearchCompPart(c, searchQueries, true);

		long[] crud = benchmarkCompCrudPart(c, mutateSamples, true);

		BenchmarkResult.create("压缩树-10分区-反序", "压缩树 10分区 反序", totalDataCount, loadMs, memGb, false)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// CRUD 基准测试工具方法
	// ========================================================================

	/** 从 DB 取样本数据，返回 [name, code] 对 */
	private static List<String[]> fetchSamples(int count) throws Exception {
		List<String[]> list = new ArrayList<>();
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_name, company_code FROM company_info ORDER BY RANDOM() LIMIT " + count)) {
			while (rs.next()) {
				list.add(new String[]{rs.getString(1), String.valueOf(rs.getLong(2))});
			}
		}
		return list;
	}

	/**
	 * 生成多样化短文本查询样本，模拟线上真实查询场景：
	 * - 精确公司名（30%）：从DB取完整公司名，命中/未命中取决于数据是否在全量中
	 * - 短片段子串（25%）：从公司名中随机截2~15字符，模拟用户输片段搜索
	 * - 多公司拼接（15%）：2~3个公司名片段拼在一起，模拟OCR多框识别
	 * - 随机地域+字号（15%）：模拟搜索关键词，可能是部分公司名
	 * - 纯脏数据/噪声（15%）：特殊字符、空白、中英混排，测试异常路径
	 */
	private static List<String> generateSearchQueries(int count) throws Exception {
		Random r = new Random(42); // 固定种子可复现
		List<String> queries = new ArrayList<>(count);

		// 从DB预取公司名作为素材
		List<String> rawNames = new ArrayList<>();
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_name FROM company_info ORDER BY RANDOM() LIMIT 3000")) {
			while (rs.next()) rawNames.add(rs.getString(1));
		}

		String[] regions       = MockDataDictionary.REGIONS;
		String[] charPool      = MockDataDictionary.CHAR_POOL;
		String[] alphanumerics = MockDataDictionary.ALPHANUMERIC;
		String[] separators    = MockDataDictionary.SPECIAL_SEPARATORS;
		String[] bracketsL     = MockDataDictionary.BRACKET_LEFT;
		String[] bracketsR     = MockDataDictionary.BRACKET_RIGHT;

		int exactBase    = (int)(count * 0.30);
		int fragmentBase = (int)(count * 0.25);
		int multiBase    = (int)(count * 0.15);
		int regionBase   = (int)(count * 0.15);
		int noiseBase    = count - exactBase - fragmentBase - multiBase - regionBase;

		int idx = 0;

		// 30%: 精确公司名（从DB随机取）
		for (int i = 0; i < exactBase && idx < count; i++, idx++) {
			queries.add(rawNames.get(r.nextInt(rawNames.size())));
		}

		// 25%: 短片段子串（2~15字符，从公司名中截取）
		for (int i = 0; i < fragmentBase && idx < count; i++, idx++) {
			String full = rawNames.get(r.nextInt(rawNames.size()));
			int len = full.length();
			if (len <= 2) {
				queries.add(full);
			} else {
				int subLen = 2 + r.nextInt(Math.min(len - 1, 14));
				int start = r.nextInt(len - subLen + 1);
				queries.add(full.substring(start, start + subLen));
			}
		}

		// 15%: 多公司拼接（2~3段公司名拼接，中间加或不加分隔符）
		for (int i = 0; i < multiBase && idx < count; i++, idx++) {
			StringBuilder sb = new StringBuilder();
			int segCount = 2 + r.nextInt(2);
			for (int j = 0; j < segCount; j++) {
				String src = rawNames.get(r.nextInt(rawNames.size()));
				int subLen;
				int start;
				if (src.length() <= 2) {
					subLen = src.length();
					start = 0;
				} else {
					subLen = 2 + r.nextInt(Math.min(src.length() - 1, 8));
					start = r.nextInt(src.length() - subLen + 1);
				}
				sb.append(src, start, start + subLen);
				if (j < segCount - 1 && r.nextBoolean()) {
					sb.append(separators[r.nextInt(Math.min(5, separators.length))]);
				}
			}
			queries.add(sb.toString());
		}

		// 15%: 随机地域+字号（模拟搜索关键词）
		for (int i = 0; i < regionBase && idx < count; i++, idx++) {
			StringBuilder sb = new StringBuilder();
			sb.append(regions[r.nextInt(regions.length)]);
			int zhCount = 1 + r.nextInt(3);
			for (int j = 0; j < zhCount; j++) {
				sb.append(charPool[r.nextInt(charPool.length)]);
			}
			// 偶尔加个括号或数字
			if (r.nextBoolean()) {
				sb.append(alphanumerics[r.nextInt(alphanumerics.length)]);
			}
			queries.add(sb.toString());
		}

		// 15%: 纯脏数据/噪声
		for (int i = 0; i < noiseBase && idx < count; i++, idx++) {
			int type = r.nextInt(4);
			switch (type) {
				case 0:
					// 特殊字符+短词
					queries.add(separators[r.nextInt(separators.length)]
							+ charPool[r.nextInt(charPool.length)]
							+ bracketsL[r.nextInt(bracketsL.length)]
							+ r.nextInt(999)
							+ bracketsR[r.nextInt(bracketsR.length)]);
					break;
				case 1:
					// 纯中英数混排
					queries.add(alphanumerics[r.nextInt(alphanumerics.length)]
							+ regions[r.nextInt(regions.length)]
							+ alphanumerics[r.nextInt(alphanumerics.length)]);
					break;
				case 2:
					// 多个空白+短字符
					StringBuilder sb = new StringBuilder();
					for (int k = 0; k < 3; k++) {
						sb.append(separators[r.nextInt(Math.min(8, separators.length))]);
						sb.append(charPool[r.nextInt(charPool.length)]);
					}
					queries.add(sb.toString());
					break;
				case 3:
					// 纯短随机（2~4 字）
					queries.add(charPool[r.nextInt(charPool.length)]
							+ charPool[r.nextInt(charPool.length)]);
					break;
			}
		}

		// 打乱顺序
		Collections.shuffle(queries, r);

		// 统计命中/未命中比例（粗略）
		System.out.printf("      查询分布: 精确名=%d 片段=%d 多公司=%d 地域=%d 噪声=%d\n",
				exactBase, fragmentBase, multiBase, regionBase, noiseBase);

		return queries;
	}

	// --- 标准树单树 search ---
	private static long benchmarkSearchStdOne(StandardTrie trie, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (String q : queries) {
			trie.search(q, reverse);
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 标准树分区 search ---
	private static long benchmarkSearchStdPart(StandardTrie[] tries, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (String q : queries) {
			int pid = Math.abs(q.hashCode()) % 10;
			tries[pid].search(q, reverse);
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 压缩树单树 search ---
	private static long benchmarkSearchCompOne(CompressedTrie trie, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (String q : queries) {
			trie.search(q, reverse);
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 压缩树分区 search ---
	private static long benchmarkSearchCompPart(CompressedTrie[] tries, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (String q : queries) {
			int pid = Math.abs(q.hashCode()) % 10;
			tries[pid].search(q, reverse);
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- CRUD benchmark ---
	private static long[] benchmarkStdCrudOne(StandardTrie t, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < MUTATE_COUNT; i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			long s1 = System.nanoTime();
			t.insert("_BENCH_NEW_" + i, code + 10000000L, reverse);
			tIns += System.nanoTime() - s1;
			long s2 = System.nanoTime();
			t.delete(name, reverse);
			t.insert(name, code + 20000000L, reverse);
			tUpd += System.nanoTime() - s2;
			long s3 = System.nanoTime();
			t.delete("_BENCH_NEW_" + i, reverse);
			tDel += System.nanoTime() - s3;
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkStdCrudPart(StandardTrie[] t, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < MUTATE_COUNT; i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			int pid = (int) (code % 10);
			long newCode = code + 10000000L;
			int pidNew = (int) (newCode % 10);
			long s1 = System.nanoTime();
			t[pidNew].insert("_BENCH_NEW_" + i, newCode, reverse);
			tIns += System.nanoTime() - s1;
			long upCode = code + 20000000L;
			int pidUp = (int) (upCode % 10);
			long s2 = System.nanoTime();
			t[pid].delete(name, reverse);
			t[pidUp].insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			long s3 = System.nanoTime();
			t[pidNew].delete("_BENCH_NEW_" + i, reverse);
			tDel += System.nanoTime() - s3;
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkCompCrudOne(CompressedTrie c, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < MUTATE_COUNT; i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			long s1 = System.nanoTime();
			c.insert("_BENCH_NEW_" + i, code + 10000000L, reverse);
			tIns += System.nanoTime() - s1;
			long s2 = System.nanoTime();
			c.delete(name, reverse);
			c.insert(name, code + 20000000L, reverse);
			tUpd += System.nanoTime() - s2;
			long s3 = System.nanoTime();
			c.delete("_BENCH_NEW_" + i, reverse);
			tDel += System.nanoTime() - s3;
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkCompCrudPart(CompressedTrie[] c, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < MUTATE_COUNT; i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			int pid = (int) (code % 10);
			long newCode = code + 10000000L;
			int pidNew = (int) (newCode % 10);
			long s1 = System.nanoTime();
			c[pidNew].insert("_BENCH_NEW_" + i, newCode, reverse);
			tIns += System.nanoTime() - s1;
			long upCode = code + 20000000L;
			int pidUp = (int) (upCode % 10);
			long s2 = System.nanoTime();
			c[pid].delete(name, reverse);
			c[pidUp].insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			long s3 = System.nanoTime();
			c[pidNew].delete("_BENCH_NEW_" + i, reverse);
			tDel += System.nanoTime() - s3;
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	// ========================================================================
	// 内存/GC/文件/加载 工具方法
	// ========================================================================
	private static long gcAndGetMemory() {
		System.gc();
		System.runFinalization();
		try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	private static void printMem() {
		long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		System.out.printf("    >> 当前堆内存: %.1f GB%n", mem / 1024.0 / 1024.0 / 1024.0);
	}

	private static void loadToSingleTrie(StandardTrie trie, boolean reverse) throws Exception {
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_code, company_name FROM company_info")) {
			int count = 0;
			System.out.print("      ");
			while (rs.next()) {
				trie.insert(rs.getString(2), rs.getLong(1), reverse);
				if (++count % 100000 == 0) System.out.print(".");
			}
			System.out.println();
			System.out.println("      共加载 " + count + " 条");
		}
	}

	private static final String[] POISON = new String[0];

	private static void loadToPartitionedTries(StandardTrie[] tries, boolean reverse) throws Exception {
		// 10 个分区队列，生产者流式读 DB 放入，消费者拉取插入
		BlockingQueue<String[]>[] queues = new BlockingQueue[10];
		for (int i = 0; i < 10; i++) queues[i] = new LinkedBlockingQueue<>(20000);

		// 启动 10 个消费者线程
		Thread[] workers = new Thread[10];
		for (int pid = 0; pid < 10; pid++) {
			final int p = pid;
			workers[p] = new Thread(() -> {
				try {
					while (true) {
						String[] row = queues[p].take();
						if (row == POISON) break;
						tries[p].insert(row[0], Long.parseLong(row[1]), reverse);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}, "part-worker-" + p);
			workers[p].start();
		}

		// 生产者：流式读 DB，放入队列
		int total = 0;
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_code, company_name FROM company_info")) {
			System.out.print("      ");
			while (rs.next()) {
				long code = rs.getLong(1);
				queues[(int) (code % 10)].put(new String[]{rs.getString(2), String.valueOf(code)});
				if (++total % 100000 == 0) System.out.print(".");
			}
			System.out.println(" 共" + total + "条");
		}

		// 生产者完成，向每个队列放入毒丸
		for (int i = 0; i < 10; i++) queues[i].put(POISON);

		// 等待所有消费者退出
		for (Thread w : workers) w.join();
	}

	private static void loadToCompressedTrie(CompressedTrie c, boolean reverse) throws Exception {
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_code, company_name FROM company_info")) {
			int count = 0;
			System.out.print("      ");
			while (rs.next()) {
				c.insert(rs.getString(2), rs.getLong(1), reverse);
				if (++count % 100000 == 0) System.out.print(".");
			}
			System.out.println();
			System.out.println("      共加载 " + count + " 条");
		}
	}

	private static void loadToPartitionedCompressedTries(CompressedTrie[] tries, boolean reverse) throws Exception {
		// 10 个分区队列，生产者流式读 DB 放入，消费者拉取插入
		BlockingQueue<String[]>[] queues = new BlockingQueue[10];
		for (int i = 0; i < 10; i++) queues[i] = new LinkedBlockingQueue<>(20000);

		// 启动 10 个消费者线程
		Thread[] workers = new Thread[10];
		for (int pid = 0; pid < 10; pid++) {
			final int p = pid;
			workers[p] = new Thread(() -> {
				try {
					while (true) {
						String[] row = queues[p].take();
						if (row == POISON) break;
						tries[p].insert(row[0], Long.parseLong(row[1]), reverse);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}, "comp-worker-" + p);
			workers[p].start();
		}

		// 生产者：流式读 DB，放入队列
		int total = 0;
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_code, company_name FROM company_info")) {
			System.out.print("      ");
			while (rs.next()) {
				long code = rs.getLong(1);
				queues[(int) (code % 10)].put(new String[]{rs.getString(2), String.valueOf(code)});
				if (++total % 100000 == 0) System.out.print(".");
			}
			System.out.println(" 共" + total + "条");
		}

		// 生产者完成，向每个队列放入毒丸
		for (int i = 0; i < 10; i++) queues[i].put(POISON);

		// 等待所有消费者退出
		for (Thread w : workers) w.join();
	}

	private static String ensureDir(String folderName) {
		String path = BASE_DIR + folderName + "/";
		new File(path).mkdirs();
		return path;
	}


}
