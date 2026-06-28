package cn.com;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

@Controller
public class ReportController {

	private static final String JSON_PATH = "./src/main/java/com/myutils/trie/benchmark_report.json";
	private static final String VERIFY_PATH = "./src/main/java/com/myutils/trie/verification_samples.json";
	private static final String DB_URL = "jdbc:sqlite:trie_benchmark.db";

	@GetMapping("/")
	public String index() {
		return "benchmark_report";
	}

	@GetMapping("/verify")
	public String verify() {
		return "verify";
	}

	@GetMapping(value = "/api/results", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public String getResults() {
		File file = new File(JSON_PATH);
		if (file.exists()) {
			try {
				return new String(Files.readAllBytes(Paths.get(JSON_PATH)));
			} catch (Exception ignored) {
			}
		}
		if (!BenchmarkResult.results.isEmpty()) {
			return BenchmarkResult.resultsToJson();
		}
		return "[]";
	}

	@GetMapping(value = "/api/verify", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public String getVerifySamples(
			@RequestParam(required = false) String testName,
			@RequestParam(required = false) String type) {

		// 优先从文件读
		File file = new File(VERIFY_PATH);
		String raw = null;
		if (file.exists()) {
			try {
				raw = new String(Files.readAllBytes(Paths.get(VERIFY_PATH)));
			} catch (Exception ignored) {
			}
		}
		if (raw == null || raw.isEmpty()) {
			if (!VerificationSample.samples.isEmpty()) {
				raw = VerificationSample.toJson();
			} else {
				return "[]";
			}
		}

		// 简单过滤：如果指定了 testName 或 type，前端自行过滤
		return raw;
	}

	/**
	 * 从 DB 反查 company_name 根据 code，用于人工核对验证
	 */
	@GetMapping(value = "/api/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public String lookupByCode(@RequestParam long code) {
		try (Connection conn = DriverManager.getConnection(DB_URL);
			 PreparedStatement ps = conn.prepareStatement("SELECT company_name, company_code FROM company_info WHERE company_code = ?")) {
			ps.setLong(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return String.format("{\"found\":true,\"name\":\"%s\",\"code\":%d}",
							esc(rs.getString(1)), rs.getLong(2));
				}
			}
		} catch (Exception e) {
			return "{\"found\":false,\"error\":\"" + esc(e.getMessage()) + "\"}";
		}
		return "{\"found\":false}";
	}

	/**
	 * 从 DB 模糊搜索 company_name，用于人工核对验证
	 */
	@GetMapping(value = "/api/lookup_name", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public String lookupByName(@RequestParam String name, @RequestParam(defaultValue = "10") int limit) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		try (Connection conn = DriverManager.getConnection(DB_URL);
			 PreparedStatement ps = conn.prepareStatement("SELECT company_name, company_code FROM company_info WHERE company_name LIKE ? LIMIT ?")) {
			ps.setString(1, "%" + name + "%");
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				boolean first = true;
				while (rs.next()) {
					if (!first) sb.append(",");
					sb.append(String.format("{\"name\":\"%s\",\"code\":%d}",
							esc(rs.getString(1)), rs.getLong(2)));
					first = false;
				}
			}
		} catch (Exception e) {
			return "[]";
		}
		sb.append("]");
		return sb.toString();
	}

	private static String esc(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
