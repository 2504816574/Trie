package cn.com;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证样本记录 — 保存扫描匹配结果供人工确认。
 */
public class VerificationSample {

	public String testName;            // 所属测试方案
	public String type;                // SEARCH / INSERT / DELETE / UPDATE
	public String queryText;           // 输入短文本
	public boolean reversed;           // 是否反序
	public List<MatchResult> matches;  // 扫描匹配到的公司名列表
	public long durationNs;            // 操作耗时(纳秒)

	public static final List<VerificationSample> samples = new ArrayList<>();

	/** 记录一次 SEARCH 扫描结果 */
	public static VerificationSample search(String testName, String query, boolean reversed,
	                                         List<MatchResult> matches, long durationNs) {
		VerificationSample s = new VerificationSample();
		s.testName = testName;
		s.type = "SEARCH";
		s.queryText = query;
		s.reversed = reversed;
		s.matches = matches;
		s.durationNs = durationNs;
		samples.add(s);
		return s;
	}

	/** 记录一次 CRUD 验证结果（单条 code 命中/未命中） */
	public static VerificationSample crud(String testName, String type, String text,
	                                       boolean reversed, long code, long durationNs) {
		VerificationSample s = new VerificationSample();
		s.testName = testName;
		s.type = type;
		s.queryText = text;
		s.reversed = reversed;
		s.matches = new ArrayList<>();
		if (code >= 0) {
			s.matches.add(new MatchResult(text, code, 0, text.length()));
		}
		s.durationNs = durationNs;
		samples.add(s);
		return s;
	}

	public boolean isHit() {
		return matches != null && !matches.isEmpty();
	}

	// ---- JSON 序列化 ----

	public static String toJson() {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		for (int i = 0; i < samples.size(); i++) {
			sb.append(samples.get(i).toJsonObject());
			sb.append(i < samples.size() - 1 ? ",\n" : "\n");
		}
		sb.append("]\n");
		return sb.toString();
	}

	private String toJsonObject() {
		StringBuilder sb = new StringBuilder();
		sb.append("  {\n");
		sb.append("    \"testName\": \"").append(esc(testName)).append("\",\n");
		sb.append("    \"type\": \"").append(type).append("\",\n");
		sb.append("    \"queryText\": \"").append(esc(queryText)).append("\",\n");
		sb.append("    \"reversed\": ").append(reversed).append(",\n");
		sb.append("    \"hit\": ").append(isHit()).append(",\n");
		sb.append("    \"matchCount\": ").append(matches != null ? matches.size() : 0).append(",\n");
		sb.append("    \"durationMs\": ").append(String.format("%.4f", durationNs / 1_000_000.0)).append(",\n");
		sb.append("    \"matches\": [");
		if (matches != null && !matches.isEmpty()) {
			sb.append("\n");
			for (int i = 0; i < matches.size(); i++) {
				MatchResult m = matches.get(i);
				sb.append("      {");
				sb.append("\"matchedText\":\"").append(esc(m.matchedText)).append("\",");
				sb.append("\"companyCode\":").append(m.companyCode).append(",");
				sb.append("\"startIndex\":").append(m.startIndex).append(",");
				sb.append("\"endIndex\":").append(m.endIndex);
				sb.append("}");
				if (i < matches.size() - 1) sb.append(",");
				sb.append("\n");
			}
			sb.append("    ");
		}
		sb.append("]\n");
		sb.append("  }");
		return sb.toString();
	}

	private static String esc(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	public static void clear() {
		samples.clear();
	}
}
