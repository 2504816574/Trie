package cn.com;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 超高随机度企业名称生成器 — 碎片化装配 + 随机脏数据注入
 * 每个字段独立随机，不再使用固定模板，最大程度模拟线上混乱数据
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

		private final String[] regions          = MockDataDictionary.REGIONS;
		private final String[] charPool         = MockDataDictionary.CHAR_POOL;
		private final String[] bracketsL        = MockDataDictionary.BRACKET_LEFT;
		private final String[] bracketsR        = MockDataDictionary.BRACKET_RIGHT;
		private final String[] alphanumerics    = MockDataDictionary.ALPHANUMERIC;
		private final String[] separators       = MockDataDictionary.SPECIAL_SEPARATORS;
		private final String[] branchTypes      = MockDataDictionary.BRANCH_TYPES;
		private final String[] yearSuffix       = MockDataDictionary.YEAR_SUFFIX;
		private final String[] longPrefixes     = MockDataDictionary.LONG_PREFIXES;
		private final String[] codeLikePrefix   = MockDataDictionary.CODE_LIKE_PREFIX;
		private final String[] industryHeads    = MockDataDictionary.INDUSTRY_HEADS;
		private final String[] corpTypes        = MockDataDictionary.CORP_TYPES;

		// 自然人姓名索引范围（CHAR_POOL 末尾约 60 个）
		private final int personNameStart;
		private final int personNameCount = 60;

		private final List<String> suffixPool = new ArrayList<>();

		public Generator(long startCode, int total) {
			this.startCode = startCode;
			this.total = total;
			this.pos = 0;
			this.personNameStart = Math.max(0, charPool.length - personNameCount);
			// 不预热全部笛卡尔积，改为运行时随机拼，避免组合爆炸
		}

		public boolean hasNext() { return pos < total; }

		public CompanyData next() {
			StringBuilder buf = new StringBuilder(128);
			assemble(buf);
			postMutate(buf);  // 后置随机突变
			return new CompanyData(buf.toString(), startCode + pos++);
		}

		// ================================================================
		// 碎片化装配：每个位置独立随机
		// ================================================================
		private void assemble(StringBuilder buf) {
			// --- 前置碎片（随机有/无）---
			if (r.nextInt(100) < 35) {
				appendRandomFragmentFront(buf);
			}

			// --- 地域碎片（80%概率出现，可重复出现）---
			int regionCount = 1 + (r.nextInt(100) < 15 ? r.nextInt(3) : 0);  // 15%概率多地域
			for (int i = 0; i < regionCount; i++) {
				buf.append(regions[r.nextInt(regions.length)]);
			}

			// --- 注入随机括号包裹地域 ---
			if (r.nextInt(100) < 20) {
				int pos = buf.length() > 0 ? r.nextInt(buf.length()) : 0;
				String wrapped = bracketsL[r.nextInt(bracketsL.length)]
						+ regions[r.nextInt(regions.length)]
						+ bracketsR[r.nextInt(bracketsR.length)];
				buf.insert(pos, wrapped);
			}

			// --- 字号碎片（1~6个随机字号，50%概率中间插入分隔符/空格）---
			int zihaoCount = 1 + r.nextInt(6);
			for (int i = 0; i < zihaoCount; i++) {
				buf.append(charPool[r.nextInt(charPool.length)]);
				if (i < zihaoCount - 1 && r.nextInt(100) < 50) {
					// 50%概率在字号之间插入分隔符、括号或中英数字
					injectNoise(buf);
				}
			}

			// --- 年份/括号后缀（随机穿插在字号后）---
			if (r.nextInt(100) < 25) {
				buf.append(bracketsL[r.nextInt(bracketsL.length)])
					.append(yearSuffix[r.nextInt(yearSuffix.length)])
					.append(bracketsR[r.nextInt(bracketsR.length)]);
			}

			// --- 随机在中段注入中英文/数字/代码片段 ---
			if (r.nextInt(100) < 30) {
				buf.append(alphanumerics[r.nextInt(alphanumerics.length)]);
			}

			// --- 再次注入地域（模拟"北京上海XX公司"这种跨地域名）---
			if (r.nextInt(100) < 12) {
				buf.append(regions[r.nextInt(regions.length)]);
			}

			// --- 行业（85%概率出现，可多行业并行）---
			if (r.nextInt(100) < 85) {
				int indCount = 1 + (r.nextInt(100) < 10 ? r.nextInt(2) : 0);
				for (int i = 0; i < indCount; i++) {
					buf.append(industryHeads[r.nextInt(industryHeads.length)]);
				}
			}

			// --- 组织形式（可多个叠加，线上线下常见）---
			// 50%概率无组织形式（个体工商户、脏数据等场景）
			if (r.nextInt(100) < 50) {
				int corpCount = 1 + (r.nextInt(100) < 20 ? r.nextInt(2) : 0);
				for (int i = 0; i < corpCount; i++) {
					buf.append(corpTypes[r.nextInt(corpTypes.length)]);
					// 10%概率中间插入噪声
					if (i < corpCount - 1 && r.nextInt(100) < 10) {
						injectNoise(buf);
					}
				}
			}

			// --- 分支机构后缀 ---
			if (r.nextInt(100) < 20) {
				if (r.nextInt(100) < 40) {
					buf.append("第").append(r.nextInt(99999) + 1);
				}
				buf.append(branchTypes[r.nextInt(branchTypes.length)]);
			}

			// --- 长尾编号 ---
			if (r.nextInt(100) < 12) {
				buf.append(r.nextInt(100000) + 1);
			}

			// --- 尾缀随机噪声片段 ---
			if (r.nextInt(100) < 8) {
				appendRandomFragmentBack(buf);
			}
		}

		// ================================================================
		// 前置片段生成器
		// ================================================================
		private void appendRandomFragmentFront(StringBuilder buf) {
			int type = r.nextInt(4);
			switch (type) {
				case 0:
					// 央企/集团长前缀
					buf.append(longPrefixes[r.nextInt(longPrefixes.length)])
					   .append(separators[r.nextInt(separators.length)]);
					break;
				case 1:
					// OCR信用代码混入
					buf.append(codeLikePrefix[r.nextInt(codeLikePrefix.length)])
					   .append(separators[r.nextInt(separators.length)]);
					break;
				case 2:
					// 中英数混排前缀
					buf.append(alphanumerics[r.nextInt(alphanumerics.length)])
					   .append(separators[r.nextInt(separators.length)]);
					break;
				case 3:
					// 纯噪声前缀
					for (int i = 0; i < 1 + r.nextInt(3); i++) {
						buf.append(charPool[r.nextInt(charPool.length)]);
						injectNoise(buf);
					}
					break;
			}
		}

		// ================================================================
		// 尾缀片段生成器
		// ================================================================
		private void appendRandomFragmentBack(StringBuilder buf) {
			int type = r.nextInt(3);
			switch (type) {
				case 0:
					// 再追加一层年份+组织形式
					buf.append(bracketsL[r.nextInt(bracketsL.length)])
					   .append(yearSuffix[r.nextInt(yearSuffix.length)])
					   .append(bracketsR[r.nextInt(bracketsR.length)])
					   .append(corpTypes[r.nextInt(corpTypes.length)]);
					break;
				case 1:
					// 追加信用代码片段
					buf.append("_").append(codeLikePrefix[r.nextInt(codeLikePrefix.length)]);
					break;
				case 2:
					// 追加多层分支
					buf.append(branchTypes[r.nextInt(branchTypes.length)])
					   .append("第").append(r.nextInt(999) + 1).append("部");
					break;
			}
		}

		// ================================================================
		// 噪声注入：分隔符 / 括号 / 中英混排 / 空格
		// ================================================================
		private void injectNoise(StringBuilder buf) {
			int type = r.nextInt(5);
			switch (type) {
				case 0:
					buf.append(separators[r.nextInt(separators.length)]);
					break;
				case 1:
					// 括号包裹随机内容
					buf.append(bracketsL[r.nextInt(bracketsL.length)])
					   .append(charPool[r.nextInt(charPool.length)])
					   .append(bracketsR[r.nextInt(bracketsR.length)]);
					break;
				case 2:
					buf.append(alphanumerics[r.nextInt(alphanumerics.length)]);
					break;
				case 3:
					// 多个空格/特殊空白
					int c = 1 + r.nextInt(4);
					for (int i = 0; i < c; i++) {
						buf.append(separators[r.nextInt(
								Math.min(8, separators.length))]);  // 优先取空格类
					}
					break;
				case 4:
					// 纯数字编号
					buf.append("[").append(r.nextInt(9999)).append("]");
					break;
			}
		}

		// ================================================================
		// 后置随机突变：模拟人工录入的各种错误
		// ================================================================
		private void postMutate(StringBuilder buf) {
			if (buf.length() == 0) return;

			// 随机注入空格到任意位置（15%概率）
			if (r.nextInt(100) < 15) {
				int insPos = r.nextInt(buf.length());
				String sp = separators[r.nextInt(Math.min(8, separators.length))];
				buf.insert(insPos, sp);
			}

			// 随机重复某个字符/片段（8%概率，模拟卡键）
			if (r.nextInt(100) < 8) {
				int repPos = r.nextInt(buf.length());
				int repLen = 1 + r.nextInt(3);
				int end = Math.min(repPos + repLen, buf.length());
				String dup = buf.substring(repPos, end);
				buf.insert(repPos, dup);
			}

			// 随机在末尾追加空格/不可见字符（12%概率）
			if (r.nextInt(100) < 12) {
				for (int i = 0; i < 1 + r.nextInt(3); i++) {
					buf.append(separators[r.nextInt(Math.min(8, separators.length))]);
				}
			}

			// 随机在前面追加空格/BOM类字符（5%概率）
			if (r.nextInt(100) < 5) {
				buf.insert(0, separators[r.nextInt(Math.min(8, separators.length))]);
			}

			// 随机替换某个括号为异形括号（10%概率）
			if (r.nextInt(100) < 10) {
				randomlyFlipBrackets(buf);
			}

			// 随机插入自然人姓名到中间（8%概率）
			if (r.nextInt(100) < 8) {
				int insPos = r.nextInt(buf.length());
				String person = charPool[personNameStart + r.nextInt(personNameCount)];
				buf.insert(insPos, person);
			}

			// 随机大小写/全半角污染（5%概率，对ASCII字符做全半角转换）
			if (r.nextInt(100) < 5) {
				corruptAscii(buf);
			}
		}

		/**
		 * 随机将字符串中的括号替换为异形括号
		 */
		private void randomlyFlipBrackets(StringBuilder buf) {
			String s = buf.toString();
			// 找到所有括号位置并随机替换
			for (int idx = 0; idx < buf.length(); idx++) {
				char ch = buf.charAt(idx);
				String c = String.valueOf(ch);
				for (int i = 0; i < bracketsL.length; i++) {
					if (c.equals(bracketsL[i])) {
						buf.replace(idx, idx + 1, bracketsL[r.nextInt(bracketsL.length)]);
						break;
					}
					if (i < bracketsR.length && c.equals(bracketsR[i])) {
						buf.replace(idx, idx + 1, bracketsR[r.nextInt(bracketsR.length)]);
						break;
					}
				}
			}
		}

		/**
		 * 对字符串中的ASCII字符做随机全半角/大小写污染
		 */
		private void corruptAscii(StringBuilder buf) {
			for (int i = 0; i < buf.length(); i++) {
				char ch = buf.charAt(i);
				if (ch >= 'a' && ch <= 'z' && r.nextInt(100) < 25) {
					buf.setCharAt(i, (char) (ch - 32)); // 转大写
				} else if (ch >= 'A' && ch <= 'Z' && r.nextInt(100) < 25) {
					buf.setCharAt(i, (char) (ch + 32)); // 转小写
				} else if (ch >= '0' && ch <= '9' && r.nextInt(100) < 10) {
					// 数字转全角
					buf.setCharAt(i, (char) (ch + 0xFEE0));
				}
			}
		}
	}
}
