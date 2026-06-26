package cn.com;

import java.util.Random;

/**
 * 企业名称随机生成器 — 遵循《企业名称登记管理规定》结构
 * 生成格式：行政区划名称 + 字号(≥2汉字) + 行业或经营特点 + 组织形式
 */
public class DataSimulator {

	public static class CompanyData {
		public String name;
		public long code;

		public CompanyData(String name, long code) {
			this.name = name;
			this.code = code;
		}
	}

	public static class Generator {
		private final int total;
		private final long startCode;
		private int pos;
		private final Random r = new Random();

		private final String[] regions       = MockDataDictionary.REGIONS;
		private final String[] charPool      = MockDataDictionary.CHAR_POOL;
		private final String[] branchTypes   = MockDataDictionary.BRANCH_TYPES;
		private final String[] yearSuffix    = MockDataDictionary.YEAR_SUFFIX;
		private final String[] industryHeads = MockDataDictionary.INDUSTRY_HEADS;
		private final String[] corpTypes     = MockDataDictionary.CORP_TYPES;

		public Generator(long startCode, int total) {
			this.startCode = startCode;
			this.total = total;
			this.pos = 0;
		}

		public boolean hasNext() { return pos < total; }

		public CompanyData next() {
			StringBuilder buf = new StringBuilder(128);
			assemble(buf);
			return new CompanyData(buf.toString(), startCode + pos++);
		}

		// ================================================================
		// 合规装配：遵循《企业名称登记管理规定》第六、八条结构
		//   行政区划名称 + 字号(≥2个汉字) + 行业或经营特点 + 组织形式
		//   可附加年份后缀、分支机构后缀模拟真实脏数据
		// ================================================================
		private void assemble(StringBuilder buf) {
			// 1. 行政区划名称（必须，单次选取）
			buf.append(regions[r.nextInt(regions.length)]);

			// 2. 字号（≥2个汉字，由2~4个字号片段拼接）
			int zihaoCount = 2 + r.nextInt(3);
			for (int i = 0; i < zihaoCount; i++) {
				buf.append(charPool[r.nextInt(charPool.length)]);
			}

			// 3. 行业或者经营特点（85%概率出现）
			if (r.nextInt(100) < 85) {
				buf.append(industryHeads[r.nextInt(industryHeads.length)]);
			}

			// 4. 组织形式（90%概率出现）
			if (r.nextInt(100) < 90) {
				buf.append(corpTypes[r.nextInt(corpTypes.length)]);
			}

			// 5. 可选：年份后缀（如 2024年、二〇二三）
			if (r.nextInt(100) < 15) {
				buf.append(yearSuffix[r.nextInt(yearSuffix.length)]);
			}

			// 6. 可选：分支机构后缀（如 分公司、第一营业部）
			if (r.nextInt(100) < 20) {
				buf.append(branchTypes[r.nextInt(branchTypes.length)]);
			}

			// 7. 长尾编号后缀（如 001、12345）
			if (r.nextInt(100) < 8) {
				buf.append(r.nextInt(100000) + 1);
			}
		}
	}
}
