package cn.com;


import java.sql.*;

/**
 * 模块一：获取仿真数据并流式落盘到 SQLite (构造至目标总量版)
 */
public class TrieDataInitializer {

	public static final String DB_URL = "jdbc:sqlite:trie_benchmark.db";
	private static final int MOCK_DATA_SIZE = 120000000; // 目标总数据量

	public static void main(String[] args) {
		System.out.println("===============================================================");
		System.out.println(" 💾 模块一：构造高仿真工商数据至目标总量 " + MOCK_DATA_SIZE + " 条至本地 SQLite");
		System.out.println("===============================================================");

		try (Connection conn = DriverManager.getConnection(DB_URL)) {
			conn.setAutoCommit(false);

			// 1. 确保表结构存在
			try (Statement stmt = conn.createStatement()) {
				stmt.execute("CREATE TABLE IF NOT EXISTS company_info (" +
						"company_code INTEGER PRIMARY KEY," +
						"company_name TEXT NOT NULL" +
						")");
			}

			// 2. 探测当前数据库总量及最大 code
			long currentCount = 0;
			long startCode = 1000000L;
			try (Statement stmt = conn.createStatement()) {
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*), COALESCE(MAX(company_code), 0) FROM company_info");
				if (rs.next()) {
					currentCount = rs.getLong(1);
					long maxCode = rs.getLong(2);
					if (maxCode > 0) {
						startCode = maxCode + 1;
					}
				}
			}

			if (currentCount >= MOCK_DATA_SIZE) {
				System.out.println(">> 当前已有 " + currentCount + " 条，已达到目标 " + MOCK_DATA_SIZE + " 条，无需追加。");
				return;
			}

			int needCount = (int) (MOCK_DATA_SIZE - currentCount);
			System.out.println(">> 当前 " + currentCount + " 条，还需补充 " + needCount + " 条至目标 " + MOCK_DATA_SIZE + " 条");

			// 3. 流式生成并批量落盘（不入内存，一批1w条直接写DB）
			String insertSql = "INSERT INTO company_info (company_code, company_name) VALUES (?, ?)";
			try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

				int count = 0;
				int batchCount = 0;
				DataSimulator.Generator gen = new DataSimulator.Generator(startCode, needCount);

				System.out.print(">> 起始编码 [" + startCode + "]，流式生成并写入 " + needCount + " 条数据");
				while (gen.hasNext()) {
					DataSimulator.CompanyData data = gen.next();
					pstmt.setLong(1, data.code);
					pstmt.setString(2, data.name);
					pstmt.addBatch();

					if (++batchCount >= 10000) {
						pstmt.executeBatch();
						count += batchCount;
						System.out.print(".");
						batchCount = 0;
					}
				}
				if (batchCount > 0) {
					pstmt.executeBatch();
					count += batchCount;
				}
				conn.commit();
				System.out.println(" 共" + count + "条");
			}

			// 5. 统计最终总量
			try (Statement stmt = conn.createStatement();
			     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM company_info")) {
				if (rs.next()) {
					long totalCount = rs.getLong(1);
					System.out.println("\n⭐ 完成！当前 SQLite 数据库内总数据量: [" + totalCount + "] 条！");
				}
			}

		} catch (Exception e) {
			System.err.println("❌ SQLite 写入/追加失败: " + e.getMessage());
			e.printStackTrace();
		}
	}
}