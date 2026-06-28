package cn.com;

/**
 * Trie 扫描匹配结果 —— 从输入短文本中匹配到的公司名及其位置。
 */
public class MatchResult {
	public String matchedText;   // 从输入文本中匹配到的公司名子串
	public long companyCode;      // 对应的 company_code
	public int startIndex;       // 在原输入文本中的起始下标（含）
	public int endIndex;         // 在原输入文本中的结束下标（不含）

	public MatchResult(String matchedText, long companyCode, int startIndex, int endIndex) {
		this.matchedText = matchedText;
		this.companyCode = companyCode;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	@Override
	public String toString() {
		return String.format("[%s @%d-%d code=%d]", matchedText, startIndex, endIndex, companyCode);
	}
}
