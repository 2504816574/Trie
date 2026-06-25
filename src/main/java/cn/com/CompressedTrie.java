package cn.com;

import java.util.Arrays;

/**
 * 压缩前缀树（Radix Tree / Patricia Trie），纯堆内实现，支持增删查。
 * 路径压缩：单子节点路径合并为多字符 label，分叉处才创建分支。
 * 当前设计下每个分区单线程独占访问，无需锁。
 */
public class CompressedTrie {

	static class RadixNode implements Comparable<RadixNode> {
		int[] label;            // 边上的 code point 子串
		byte status = 1;        // 1=内部节点, 3=叶子节点
		long code;
		RadixNode[] children;   // 按首 code point 有序

		RadixNode() { this.label = new int[0]; }
		RadixNode(int[] label) { this.label = label; }

		@Override
		public int compareTo(RadixNode o) {
			return Integer.compare(this.label[0], o.label[0]);
		}
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
		if (node.children == null || pos >= cps.length) {
			return (node.status == 3 && pos >= cps.length) ? node.code : -1;
		}

		RadixNode child = findChild(node, cps[pos]);
		if (child == null) return -1;

		if (!matchLabel(child.label, cps, pos)) return -1;
		pos += child.label.length;

		return search(child, cps, pos);
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

		if (parent.children == null) parent.children = new RadixNode[0];

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
	 * @return true 表示该子节点应被父节点移除
	 */
	private boolean delete(RadixNode node, int[] cps, int pos) {
		if (pos >= cps.length) {
			if (node.status != 3) return false;
			node.status = 1;
			node.code = 0;
			return node.children == null || node.children.length == 0;
		}

		if (node.children == null) return false;

		RadixNode child = findChild(node, cps[pos]);
		if (child == null) return false;
		if (!matchLabel(child.label, cps, pos)) return false;

		boolean shouldRemove = delete(child, cps, pos + child.label.length);
		if (!shouldRemove) {
			mergeIfSingleChild(node);
			return false;
		}

		removeChild(node, child);

		if (node.status != 3 && node.children != null && node.children.length == 1) {
			mergeWithChild(node, node.children[0]);
			return node == root ? false : true; // root 不消除
		}
		return node.children == null || node.children.length == 0;
	}

	private void mergeWithChild(RadixNode parent, RadixNode child) {
		int[] merged = new int[parent.label.length + child.label.length];
		System.arraycopy(parent.label, 0, merged, 0, parent.label.length);
		System.arraycopy(child.label, 0, merged, parent.label.length, child.label.length);
		parent.label = merged;
		parent.status = child.status;
		parent.code = child.code;
		parent.children = child.children;
	}

	private void mergeIfSingleChild(RadixNode node) {
		if (node.status == 3) return;
		if (node.children == null || node.children.length != 1) return;
		mergeWithChild(node, node.children[0]);
	}

	// ========================================================================
	// 工具方法
	// ========================================================================

	private static RadixNode findChild(RadixNode node, int firstCp) {
		RadixNode dummy = new RadixNode(new int[]{ firstCp });
		int idx = Arrays.binarySearch(node.children, dummy);
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

	private static void addChild(RadixNode parent, RadixNode child) {
		RadixNode[] old = parent.children;
		RadixNode[] arr = new RadixNode[old.length + 1];
		int idx = Arrays.binarySearch(old, child);
		int insertAt = idx < 0 ? -(idx + 1) : idx;
		System.arraycopy(old, 0, arr, 0, insertAt);
		arr[insertAt] = child;
		System.arraycopy(old, insertAt, arr, insertAt + 1, old.length - insertAt);
		parent.children = arr;
	}

	private static void replaceChild(RadixNode parent, RadixNode oldChild, RadixNode newChild) {
		for (int i = 0; i < parent.children.length; i++) {
			if (parent.children[i] == oldChild) {
				parent.children[i] = newChild;
				return;
			}
		}
	}

	private static void removeChild(RadixNode parent, RadixNode child) {
		RadixNode[] old = parent.children;
		for (int i = 0; i < old.length; i++) {
			if (old[i] == child) {
				RadixNode[] arr = new RadixNode[old.length - 1];
				System.arraycopy(old, 0, arr, 0, i);
				System.arraycopy(old, i + 1, arr, i, old.length - i - 1);
				parent.children = arr;
				return;
			}
		}
	}
}
