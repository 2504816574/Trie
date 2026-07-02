package cn.com;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 压缩前缀树（Radix Tree / Patricia Trie），纯堆内实现，支持增删查。
 * 路径压缩：单子节点路径合并为多字符 label，分叉处才创建分支。
 * 当前设计下每个分区单线程独占访问，无需锁。
 */
public class CompressedTrie {

	static class RadixNode implements Comparable<RadixNode> {
		int[] label;                // 边上的 code point 子串
		byte status = 1;            // 1=内部节点, 3=叶子节点
		long code;
		RadixNode[] children;       // 物理数组，按首 code point 有序
		int childrenCount;          // 有效子节点数量（逻辑长度），<= children.length

		RadixNode() { this.label = new int[0]; }
		RadixNode(int[] label) { this.label = label; }

		@Override
		public int compareTo(RadixNode o) {
			return Integer.compare(this.label[0], o.label[0]);
		}

		boolean hasChildren() { return childrenCount > 0; }
	}

	private final RadixNode root = new RadixNode();

	// ========================================================================
	// 检索
	// ========================================================================

	public long search(String word, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		return search(root, cps, 0);
	}

	private long search(RadixNode node, int[] cps, int pos) {
		if (!node.hasChildren() || pos >= cps.length) {
			return (node.status == 3 && pos >= cps.length) ? node.code : -1;
		}

		RadixNode child = findChild(node, cps[pos]);
		if (child == null) return -1;

		if (!matchLabel(child.label, cps, pos)) return -1;
		pos += child.label.length;

		return search(child, cps, pos);
	}

	/**
	 * 扫描短文本，从每个字符位置出发尝试匹配 Trie 中的公司名。
	 * 返回所有匹配到的公司名、code 及在原文本中的下标。
	 */
	public List<MatchResult> scan(String text, boolean reverse) {
		String target = reverse ? new StringBuilder(text).reverse().toString() : text;
		int[] cps = target.codePoints().toArray();
		List<MatchResult> results = new ArrayList<>();

		int originalLen = text.length();
		for (int start = 0; start < cps.length; start++) {
			scanFrom(root, cps, start, start, results, text, originalLen, reverse);
		}
		return results;
	}

	/**
	 * 从 node 出发，匹配 cps 中从 pos 开始的字符，累积 start 作为扫描起点。
	 */
	private void scanFrom(RadixNode node, int[] cps, int scanStart, int pos,
	                       List<MatchResult> results, String text, int originalLen, boolean reverse) {
		if (node.status == 3 && pos > scanStart) {
			int matchStart, matchEnd;
			if (reverse) {
				matchStart = originalLen - pos - 1 + 1;  // +1 因为 pos 已走到下一个字符
				matchEnd = originalLen - scanStart;
			} else {
				matchStart = scanStart;
				matchEnd = pos;
			}
			// 仅当 end > start 时才是有效匹配（排除空 label 的根节点）
			if (matchEnd > matchStart) {
				String matchedText = text.substring(matchStart, matchEnd);
				results.add(new MatchResult(matchedText, node.code, matchStart, matchEnd));
			}
		}

		if (pos >= cps.length || !node.hasChildren()) return;

		RadixNode child = findChild(node, cps[pos]);
		if (child == null) return;
		if (!matchLabel(child.label, cps, pos)) return;

		scanFrom(child, cps, scanStart, pos + child.label.length, results, text, originalLen, reverse);
	}

	// ========================================================================
	// 插入
	// ========================================================================

	public void insert(String word, long code, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		insert(root, cps, 0, code);
	}

	private void insert(RadixNode parent, int[] remaining, int offset, long code) {
		if (remaining.length == offset) {
			parent.status = 3;
			parent.code = code;
			return;
		}

		if (parent.children == null) parent.children = new RadixNode[4];

		RadixNode match = findChild(parent, remaining[offset]);
		if (match == null) {
			RadixNode leaf = new RadixNode(Arrays.copyOfRange(remaining, offset, remaining.length));
			leaf.status = 3;
			leaf.code = code;
			addChild(parent, leaf);
			return;
		}

		int commonLen = commonPrefixLen(match.label, 0, remaining, offset);
		if (commonLen == match.label.length) {
			insert(match, remaining, offset + commonLen, code);
			return;
		}

		// 分裂
		int[] splitLabel = Arrays.copyOf(match.label, commonLen);
		int[] matchRest = Arrays.copyOfRange(match.label, commonLen, match.label.length);

		RadixNode splitNode = new RadixNode(splitLabel);
		match.label = matchRest;
		splitNode.children = new RadixNode[]{ match };
		splitNode.childrenCount = 1;
		replaceChild(parent, match, splitNode);

		if (remaining.length == offset + commonLen) {
			splitNode.status = 3;
			splitNode.code = code;
		} else {
			int[] remainingRest = Arrays.copyOfRange(remaining, offset + commonLen, remaining.length);
			RadixNode leaf = new RadixNode(remainingRest);
			leaf.status = 3;
			leaf.code = code;
			addChild(splitNode, leaf);
		}
	}

	// ========================================================================
	// 删除
	// ========================================================================

	public boolean delete(String word, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		return delete(root, cps, 0);
	}

	/**
	 * @return true 表示目标词存在并已删除
	 */
	private boolean delete(RadixNode node, int[] cps, int pos) {
		if (pos >= cps.length) {
			if (node.status != 3) return false;
			node.status = 1;
			node.code = 0;
			return true;
		}

		if (!node.hasChildren()) return false;

		RadixNode child = findChild(node, cps[pos]);
		if (child == null) return false;
		if (!matchLabel(child.label, cps, pos)) return false;

		boolean deleted = delete(child, cps, pos + child.label.length);
		if (!deleted) return false;

		if (child.status != 3) {
			if (!child.hasChildren()) {
				removeChild(node, child);
			} else if (child.childrenCount == 1) {
				mergeWithChild(child, child.children[0]);
			}
		}
		return true;
	}

	private void mergeWithChild(RadixNode parent, RadixNode child) {
		int[] merged = new int[parent.label.length + child.label.length];
		System.arraycopy(parent.label, 0, merged, 0, parent.label.length);
		System.arraycopy(child.label, 0, merged, parent.label.length, child.label.length);
		parent.label = merged;
		parent.status = child.status;
		parent.code = child.code;
		parent.children = child.children;
		parent.childrenCount = child.childrenCount;
	}

	// ========================================================================
	// 工具方法
	// ========================================================================

	private static RadixNode findChild(RadixNode node, int firstCp) {
		if (node.childrenCount == 0) return null;
		RadixNode dummy = new RadixNode(new int[]{ firstCp });
		int idx = Arrays.binarySearch(node.children, 0, node.childrenCount, dummy);
		return idx >= 0 ? node.children[idx] : null;
	}

	private static boolean matchLabel(int[] label, int[] cps, int pos) {
		if (pos + label.length > cps.length) return false;
		for (int i = 0; i < label.length; i++) {
			if (label[i] != cps[pos + i]) return false;
		}
		return true;
	}

	private static int commonPrefixLen(int[] label, int labelOff, int[] cps, int cpsOff) {
		int max = Math.min(label.length - labelOff, cps.length - cpsOff);
		for (int i = 0; i < max; i++) {
			if (label[labelOff + i] != cps[cpsOff + i]) return i;
		}
		return max;
	}

	/** 容量 >= 逻辑长度 + 1，否则扩容 1.5 倍 */
	private static void ensureCapacity(RadixNode parent, int need) {
		if (parent.children.length >= need) return;
		int newCap = parent.children.length + (parent.children.length >> 1); // ×1.5
		if (newCap < need) newCap = need;
		parent.children = Arrays.copyOf(parent.children, newCap);
	}

	private static void addChild(RadixNode parent, RadixNode child) {
		ensureCapacity(parent, parent.childrenCount + 1);
		RadixNode[] arr = parent.children;
		// 二分查找插入位置
		RadixNode dummy = new RadixNode(child.label);
		int idx = Arrays.binarySearch(arr, 0, parent.childrenCount, dummy);
		int insertAt = idx < 0 ? -(idx + 1) : idx;
		// 后移 [insertAt, childrenCount-1]
		System.arraycopy(arr, insertAt, arr, insertAt + 1, parent.childrenCount - insertAt);
		arr[insertAt] = child;
		parent.childrenCount++;
	}

	private static void replaceChild(RadixNode parent, RadixNode oldChild, RadixNode newChild) {
		RadixNode[] arr = parent.children;
		int limit = parent.childrenCount;
		for (int i = 0; i < limit; i++) {
			if (arr[i] == oldChild) {
				arr[i] = newChild;
				return;
			}
		}
	}

	private static void removeChild(RadixNode parent, RadixNode child) {
		RadixNode[] arr = parent.children;
		int limit = parent.childrenCount;
		for (int i = 0; i < limit; i++) {
			if (arr[i] == child) {
				// 前移 [i+1, limit-1]
				System.arraycopy(arr, i + 1, arr, i, limit - i - 1);
				arr[limit - 1] = null; // help GC
				parent.childrenCount--;
				return;
			}
		}
	}

	public long countNodes() {
		return countNodesRecursive(root);
	}

	private long countNodesRecursive(RadixNode node) {
		if (node == null) return 0;
		long count = 1; // 当前节点
		if (node.children != null) {
			for (int i = 0; i < node.childrenCount; i++) {
				count += countNodesRecursive(node.children[i]);
			}
		}
		return count;
	}
}
