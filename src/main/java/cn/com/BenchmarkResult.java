package cn.com;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkResult {
	public final String testName, loadMode;
	public final int dataCount;
	public final long loadTimeMs;
	public final double memoryGB;
	public final boolean immutable;

	public long searchTimeMs, insertTimeMs, updateTimeMs, deleteTimeMs;
	public int searchOps, insertOps, updateOps, deleteOps;
	public long nodeCount = -1;

	public static final List<BenchmarkResult> results = new ArrayList<>();

	private BenchmarkResult(String testName, String loadMode, int dataCount, long loadMs, double memGb, boolean immutable) {
		this.testName = testName;
		this.loadMode = loadMode;
		this.dataCount = dataCount;
		this.loadTimeMs = loadMs;
		this.memoryGB = memGb;
		this.immutable = immutable;
	}

	public static BenchmarkResult create(String testName, String loadMode, int dataCount, long loadMs, double memGb, boolean immutable) {
		return new BenchmarkResult(testName, loadMode, dataCount, loadMs, memGb, immutable);
	}

	public BenchmarkResult nodes(long count) {
		this.nodeCount = count;
		return this;
	}

	public BenchmarkResult search(long timeMs, int ops) {
		this.searchTimeMs = timeMs;
		this.searchOps = ops;
		return this;
	}

	public BenchmarkResult crud(long insertMs, long updateMs, long deleteMs, int ops) {
		this.insertTimeMs = insertMs;
		this.insertOps = ops;
		this.updateTimeMs = updateMs;
		this.updateOps = ops;
		this.deleteTimeMs = deleteMs;
		this.deleteOps = ops;
		return this;
	}

	public void report() {
		results.add(this);
		System.out.printf("    [报表] %s | 数据量:%d | 节点数:%,d | 内存:%.1fG | 加载:%dms | 查:%dms(%d次) | 增:%dms(%d次) | 改:%dms(%d次) | 删:%dms(%d次)%n",
				testName, dataCount, nodeCount, memoryGB, loadTimeMs,
				searchTimeMs, searchOps,
				immutable ? -1 : insertTimeMs, immutable ? -1 : insertOps,
				immutable ? -1 : updateTimeMs, immutable ? -1 : updateOps,
				immutable ? -1 : deleteTimeMs, immutable ? -1 : deleteOps);
	}

	// ---- JSON 序列化 ----

	public static String resultsToJson() {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		for (int i = 0; i < results.size(); i++) {
			sb.append(results.get(i).toJson());
			sb.append(i < results.size() - 1 ? ",\n" : "\n");
		}
		sb.append("]\n");
		return sb.toString();
	}

	private String toJson() {
		double memoryMB = memoryGB * 1024;
		StringBuilder sb = new StringBuilder();
		sb.append("  {\n");
		sb.append("    \"testName\": \"").append(esc(testName)).append("\",\n");
		sb.append("    \"loadMode\": \"").append(esc(loadMode)).append("\",\n");
		sb.append("    \"dataCount\": ").append(dataCount).append(",\n");
		sb.append("    \"nodeCount\": ").append(nodeCount).append(",\n");
		sb.append("    \"memoryMB\": ").append(Math.round(memoryMB * 10) / 10.0).append(",\n");
		sb.append("    \"memoryGB\": ").append(memoryGB).append(",\n");
		sb.append("    \"loadTimeMs\": ").append(loadTimeMs).append(",\n");
		sb.append("    \"searchTimeMs\": ").append(searchTimeMs).append(",\n");
		sb.append("    \"searchOps\": ").append(searchOps).append(",\n");
		sb.append("    \"insertTimeMs\": ").append(immutable ? -1 : insertTimeMs).append(",\n");
		sb.append("    \"insertOps\": ").append(immutable ? -1 : insertOps).append(",\n");
		sb.append("    \"updateTimeMs\": ").append(immutable ? -1 : updateTimeMs).append(",\n");
		sb.append("    \"updateOps\": ").append(immutable ? -1 : updateOps).append(",\n");
		sb.append("    \"deleteTimeMs\": ").append(immutable ? -1 : deleteTimeMs).append(",\n");
		sb.append("    \"deleteOps\": ").append(immutable ? -1 : deleteOps).append(",\n");
		sb.append("    \"immutable\": ").append(immutable).append("\n");
		sb.append("  }");
		return sb.toString();
	}

	private static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
