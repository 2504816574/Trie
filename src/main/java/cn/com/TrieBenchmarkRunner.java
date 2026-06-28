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
	private static final int[] COMP_PART_SUFFIXES = {0, 1, 2, 3, 4, 5, 6};
	private static final int VERIFY_SAMPLE_COUNT = 300;
	private static final int VERIFY_CRUD_COUNT = 100;
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
		long jvmBaseMem = gcAndGetMemory();
		System.out.println(">> JVM 基准内存: " + (jvmBaseMem / 1024 / 1024) + " MB\n");

		// 预取 CRUD 样本数据
		System.out.println(">> 预取增删改样本 " + MUTATE_COUNT + " 条...");
//		List<String[]> mutateSamples =new ArrayList<>();
		List<String[]> mutateSamples = fetchSamples(MUTATE_COUNT);
		System.out.println(">> 增删改样本获取完成");
		System.out.println(">> 生成多样化查询样本 " + mutateSamples.size() + " 条(含精确/片段/脏数据/多公司)...");
//		List<String> searchQueries = new ArrayList<>();
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
//		test4_std_part_reverse(searchQueries, m utateSamples);


		// 压缩前缀树：7分区+正序，只加载尾缀0-6的分区
		test7_comp_part_normal(searchQueries, mutateSamples);
		// 压缩前缀树：7分区+反序，只加载尾缀0-6的分区
		test8_comp_part_reverse(searchQueries, mutateSamples);

		// 压缩前缀树(Radix Tree)：路径压缩合并单子节点，正序入树，节点数远少于标准树
		test5_comp_unpart_normal(searchQueries, mutateSamples);
		// 压缩前缀树：反转入树，结合路径压缩+后缀同质化，节点数最少
		test6_comp_unpart_reverse(searchQueries, mutateSamples);

		// 生成报表
		new File(BASE_DIR).mkdirs();
		String json = BenchmarkResult.resultsToJson();
		String jsonPath = BASE_DIR + "benchmark_report.json";
		try (FileWriter fw = new FileWriter(jsonPath)) { fw.write(json); }
		System.out.println("\n 报表已生成: " + jsonPath);

		// 保存验证样本
		String verifyJson = VerificationSample.toJson();
		String verifyPath = BASE_DIR + "verification_samples.json";
		try (FileWriter fw = new FileWriter(verifyPath)) { fw.write(verifyJson); }
		System.out.println(" 验证样本已生成: " + verifyPath + " (" + VerificationSample.samples.size() + " 条记录)");

		System.out.println("请用浏览器打开: " + BASE_DIR + "verify.html");
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
		long nodeCnt = t.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchStdOne("标准树-不分区-正序", t, searchQueries, false);

		long[] crud = benchmarkStdCrudOne("标准树-不分区-正序", t, mutateSamples, false);

		BenchmarkResult.create("标准树-不分区-正序", "标准树 不分区 正序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
		long nodeCnt = t.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchStdOne("标准树-不分区-反序", t, searchQueries, true);

		long[] crud = benchmarkStdCrudOne("标准树-不分区-反序", t, mutateSamples, true);

		BenchmarkResult.create("标准树-不分区-反序", "标准树 不分区 反序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
		long nodeCnt = 0;
		for (StandardTrie trie : t) nodeCnt += trie.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchStdPart("标准树-10分区-正序", t, searchQueries, false);

		long[] crud = benchmarkStdCrudPart("标准树-10分区-正序", t, mutateSamples, false);

		BenchmarkResult.create("标准树-10分区-正序", "标准树 10分区 正序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
		long nodeCnt = 0;
		for (StandardTrie trie : t) nodeCnt += trie.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchStdPart("标准树-10分区-反序", t, searchQueries, true);

		long[] crud = benchmarkStdCrudPart("标准树-10分区-反序", t, mutateSamples, true);

		BenchmarkResult.create("标准树-10分区-反序", "标准树 10分区 反序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
		long nodeCnt = c.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchCompOne("压缩树-不分区-正序", c, searchQueries, false);

		long[] crud = benchmarkCompCrudOne("压缩树-不分区-正序", c, mutateSamples, false);

		BenchmarkResult.create("压缩树-不分区-正序", "压缩树 不分区 正序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
		long nodeCnt = c.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchCompOne("压缩树-不分区-反序", c, searchQueries, true);

		long[] crud = benchmarkCompCrudOne("压缩树-不分区-反序", c, mutateSamples, true);

		BenchmarkResult.create("压缩树-不分区-反序", "压缩树 不分区 反序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【7】压缩树 - 尾缀7分区(0-6) - 正序
	// ========================================================================
	private static void test7_comp_part_normal(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [7] 压缩树 - 尾缀7分区(0-6) - 正序");
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		int n = COMP_PART_SUFFIXES.length;
		CompressedTrie[] c = new CompressedTrie[n];
		for (int i = 0; i < n; i++) c[i] = new CompressedTrie();
		loadToPartitionedCompressedTries(c, false, COMP_PART_SUFFIXES);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);
		long nodeCnt = 0;
		for (CompressedTrie trie : c) nodeCnt += trie.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchCompPart("压缩树-7分区-正序", c, searchQueries, false);

		long[] crud = benchmarkCompCrudPart("压缩树-7分区-正序", c, mutateSamples, false);

		BenchmarkResult.create("压缩树-7分区-正序", "压缩树 7分区(0-6) 正序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
			.search(searchMs, SEARCH_COUNT)
			.crud(crud[0], crud[1], crud[2], MUTATE_COUNT)
			.report();

		System.out.println("    GC...");
		gcAndGetMemory();
	}

	// ========================================================================
	// 【8】压缩树 - 尾缀7分区(0-6) - 反序
	// ========================================================================
	private static void test8_comp_part_reverse(List<String> searchQueries, List<String[]> mutateSamples) throws Exception {
		System.out.println("\n>>> [8] 压缩树 - 尾缀7分区(0-6) - 反序");
		printMem();
		long baseMem = gcAndGetMemory();
		long s = System.currentTimeMillis();
		int n = COMP_PART_SUFFIXES.length;
		CompressedTrie[] c = new CompressedTrie[n];
		for (int i = 0; i < n; i++) c[i] = new CompressedTrie();
		loadToPartitionedCompressedTries(c, true, COMP_PART_SUFFIXES);
		long loadMs = System.currentTimeMillis() - s;
		double memGb = (gcAndGetMemory() - baseMem) / 1024.0 / 1024.0 / 1024.0;
		System.out.printf("    构建耗时: %d ms, 内存: %.1f GB%n", loadMs, memGb);
		long nodeCnt = 0;
		for (CompressedTrie trie : c) nodeCnt += trie.countNodes();
		System.out.printf("    节点数: %,d%n", nodeCnt);

		long searchMs = benchmarkSearchCompPart("压缩树-7分区-反序", c, searchQueries, true);

		long[] crud = benchmarkCompCrudPart("压缩树-7分区-反序", c, mutateSamples, true);

		BenchmarkResult.create("压缩树-7分区-反序", "压缩树 7分区(0-6) 反序", totalDataCount, loadMs, memGb, false)
			.nodes(nodeCnt)
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
					// 中英数字混排
					queries.add(charPool[r.nextInt(charPool.length)]
							+ r.nextInt(999)
							+ regions[r.nextInt(regions.length)]);
					break;
				case 1:
					// 纯中英数混排
					queries.add(alphanumerics[r.nextInt(alphanumerics.length)]
							+ regions[r.nextInt(regions.length)]
							+ alphanumerics[r.nextInt(alphanumerics.length)]);
					break;
				case 2:
					// 多段中英随机拼接
					StringBuilder sb = new StringBuilder();
					for (int k = 0; k < 3; k++) {
						sb.append(alphanumerics[r.nextInt(alphanumerics.length)]);
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
	private static long benchmarkSearchStdOne(String testName, StandardTrie trie, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (int i = 0; i < queries.size(); i++) {
			String q = queries.get(i);
			long t0 = System.nanoTime();
			List<MatchResult> matches = trie.scan(q, reverse);
			long dur = System.nanoTime() - t0;
			if (i < VERIFY_SAMPLE_COUNT) {
				VerificationSample.search(testName, q, reverse, matches, dur);
			}
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 标准树分区 search ---
	private static long benchmarkSearchStdPart(String testName, StandardTrie[] tries, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (int i = 0; i < queries.size(); i++) {
			String q = queries.get(i);
			int pid = Math.abs(q.hashCode()) % 10;
			long t0 = System.nanoTime();
			List<MatchResult> matches = tries[pid].scan(q, reverse);
			long dur = System.nanoTime() - t0;
			if (i < VERIFY_SAMPLE_COUNT) {
				VerificationSample.search(testName, q, reverse, matches, dur);
			}
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 压缩树单树 search ---
	private static long benchmarkSearchCompOne(String testName, CompressedTrie trie, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		for (int i = 0; i < queries.size(); i++) {
			String q = queries.get(i);
			long t0 = System.nanoTime();
			List<MatchResult> matches = trie.scan(q, reverse);
			long dur = System.nanoTime() - t0;
			if (i < VERIFY_SAMPLE_COUNT) {
				VerificationSample.search(testName, q, reverse, matches, dur);
			}
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- 压缩树分区 search ---
	private static long benchmarkSearchCompPart(String testName, CompressedTrie[] tries, List<String> queries, boolean reverse) {
		long start = System.nanoTime();
		int n = tries.length;
		for (int i = 0; i < queries.size(); i++) {
			String q = queries.get(i);
			int pid = Math.abs(q.hashCode()) % n;
			long t0 = System.nanoTime();
			List<MatchResult> matches = tries[pid].scan(q, reverse);
			long dur = System.nanoTime() - t0;
			if (i < VERIFY_SAMPLE_COUNT) {
				VerificationSample.search(testName, q, reverse, matches, dur);
			}
		}
		return (System.nanoTime() - start) / 1_000_000;
	}

	// --- CRUD benchmark ---
	private static long[] benchmarkStdCrudOne(String testName, StandardTrie t, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < samples.size(); i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);

			String newName = "_BENCH_NEW_" + i;
			long newCode = code + 10000000L;
			long s1 = System.nanoTime();
			t.insert(newName, newCode, reverse);
			tIns += System.nanoTime() - s1;
			if (i < VERIFY_CRUD_COUNT) {
				long found = t.search(newName, reverse);
				VerificationSample.crud(testName, "INSERT", newName, reverse, found, 0);
			}

			long upCode = code + 20000000L;
			long s2 = System.nanoTime();
			t.delete(name, reverse);
			t.insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			if (i < VERIFY_CRUD_COUNT) {
				long foundDel = t.search(name, reverse);
				VerificationSample.crud(testName, "UPDATE", name, reverse, foundDel, 0);
			}

			long s3 = System.nanoTime();
			t.delete(newName, reverse);
			tDel += System.nanoTime() - s3;
			if (i < VERIFY_CRUD_COUNT) {
				long foundAfterDel = t.search(newName, reverse);
				VerificationSample.crud(testName, "DELETE", newName, reverse, foundAfterDel, 0);
			}
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkStdCrudPart(String testName, StandardTrie[] t, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < samples.size(); i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			int pid = (int) (code % 10);
			long newCode = code + 10000000L;
			int pidNew = (int) (newCode % 10);

			String newName = "_BENCH_NEW_" + i;
			long s1 = System.nanoTime();
			t[pidNew].insert(newName, newCode, reverse);
			tIns += System.nanoTime() - s1;
			if (i < VERIFY_CRUD_COUNT) {
				long found = t[pidNew].search(newName, reverse);
				VerificationSample.crud(testName, "INSERT", newName, reverse, found, 0);
			}

			long upCode = code + 20000000L;
			int pidUp = (int) (upCode % 10);
			long s2 = System.nanoTime();
			t[pid].delete(name, reverse);
			t[pidUp].insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			if (i < VERIFY_CRUD_COUNT) {
				long foundDel = t[pidUp].search(name, reverse);
				VerificationSample.crud(testName, "UPDATE", name, reverse, foundDel, 0);
			}

			long s3 = System.nanoTime();
			t[pidNew].delete(newName, reverse);
			tDel += System.nanoTime() - s3;
			if (i < VERIFY_CRUD_COUNT) {
				long foundAfterDel = t[pidNew].search(newName, reverse);
				VerificationSample.crud(testName, "DELETE", newName, reverse, foundAfterDel, 0);
			}
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkCompCrudOne(String testName, CompressedTrie c, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		for (int i = 0; i < samples.size(); i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);

			String newName = "_BENCH_NEW_" + i;
			long newCode = code + 10000000L;
			long s1 = System.nanoTime();
			c.insert(newName, newCode, reverse);
			tIns += System.nanoTime() - s1;
			if (i < VERIFY_CRUD_COUNT) {
				long found = c.search(newName, reverse);
				VerificationSample.crud(testName, "INSERT", newName, reverse, found, 0);
			}

			long upCode = code + 20000000L;
			long s2 = System.nanoTime();
			c.delete(name, reverse);
			c.insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			if (i < VERIFY_CRUD_COUNT) {
				long foundDel = c.search(name, reverse);
				VerificationSample.crud(testName, "UPDATE", name, reverse, foundDel, 0);
			}

			long s3 = System.nanoTime();
			c.delete(newName, reverse);
			tDel += System.nanoTime() - s3;
			if (i < VERIFY_CRUD_COUNT) {
				long foundAfterDel = c.search(newName, reverse);
				VerificationSample.crud(testName, "DELETE", newName, reverse, foundAfterDel, 0);
			}
		}
		return new long[]{ tIns / 1_000_000, tUpd / 1_000_000, tDel / 1_000_000 };
	}

	private static long[] benchmarkCompCrudPart(String testName, CompressedTrie[] c, List<String[]> samples, boolean reverse) {
		long tIns = 0, tUpd = 0, tDel = 0;
		int partCount = c.length;
		for (int i = 0; i < samples.size(); i++) {
			String name = samples.get(i)[0];
			long code = Long.parseLong(samples.get(i)[1]);
			int pid = (int) (Math.abs(code) % partCount);
			long newCode = code + 10000000L;
			int pidNew = (int) (Math.abs(newCode) % partCount);

			String newName = "_BENCH_NEW_" + i;
			long s1 = System.nanoTime();
			c[pidNew].insert(newName, newCode, reverse);
			tIns += System.nanoTime() - s1;
			if (i < VERIFY_CRUD_COUNT) {
				long found = c[pidNew].search(newName, reverse);
				VerificationSample.crud(testName, "INSERT", newName, reverse, found, 0);
			}

			long upCode = code + 20000000L;
			int pidUp = (int) (Math.abs(upCode) % partCount);
			long s2 = System.nanoTime();
			c[pid].delete(name, reverse);
			c[pidUp].insert(name, upCode, reverse);
			tUpd += System.nanoTime() - s2;
			if (i < VERIFY_CRUD_COUNT) {
				long foundDel = c[pidUp].search(name, reverse);
				VerificationSample.crud(testName, "UPDATE", name, reverse, foundDel, 0);
			}

			long s3 = System.nanoTime();
			c[pidNew].delete(newName, reverse);
			tDel += System.nanoTime() - s3;
			if (i < VERIFY_CRUD_COUNT) {
				long foundAfterDel = c[pidNew].search(newName, reverse);
				VerificationSample.crud(testName, "DELETE", newName, reverse, foundAfterDel, 0);
			}
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

	private static void loadToPartitionedCompressedTries(CompressedTrie[] tries, boolean reverse, int[] allowedSuffixes) throws Exception {
		Set<Integer> suffixSet = new HashSet<>();
		for (int s : allowedSuffixes) suffixSet.add(s);

		int partCount = tries.length;
		BlockingQueue<String[]>[] queues = new BlockingQueue[partCount];
		for (int i = 0; i < partCount; i++) queues[i] = new LinkedBlockingQueue<>(200000);

		// 启动消费者线程
		Thread[] workers = new Thread[partCount];
		for (int pid = 0; pid < partCount; pid++) {
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

		// 生产者：流式读 DB，只加载允许的后缀分区
		int total = 0;
		try (Connection conn = DriverManager.getConnection(DB_URL);
		     Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT company_code, company_name FROM company_info")) {
			System.out.print("      ");
			while (rs.next()) {
				long code = rs.getLong(1);
				int suffix = (int) (code % 10);
				if (suffixSet.contains(suffix)) {
					queues[suffix].put(new String[]{rs.getString(2), String.valueOf(code)});
					if (++total % 100000 == 0) System.out.print(".");
				}
			}
			System.out.println(" 共" + total + "条");
		}

		// 生产者完成，向每个队列放入毒丸
		for (int i = 0; i < partCount; i++) queues[i].put(POISON);

		// 等待所有消费者退出
		for (Thread w : workers) w.join();
	}

	private static String ensureDir(String folderName) {
		String path = BASE_DIR + folderName + "/";
		new File(path).mkdirs();
		return path;
	}


}
