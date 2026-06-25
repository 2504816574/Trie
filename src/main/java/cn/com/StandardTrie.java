package cn.com;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 严格按照线上 TrieNode 对象模型构建的标准前缀树。
 * 当前设计下每个分区单线程独占访问，无需锁。
 */
public class StandardTrie {

	public static class TrieNode implements Comparable<TrieNode>, Serializable {
		private static final long serialVersionUID = 1L;
		public TrieNode[] childArray = null;
		public int c;
		public byte trieType;
		public long code;
		public byte status = 1; // 1:继续, 2:多码(此处单码处理), 3:结束
		public byte type;

		public TrieNode() {}
		public TrieNode(int c) { this.c = c; }
		public TrieNode(int c, int status, long code) {
			this.c = c;
			this.status = (byte) status;
			this.code = code;
		}

		@Override
		public int compareTo(TrieNode o) {
			return Integer.compare(this.c, o.c);
		}
	}

	private final TrieNode root = new TrieNode(0);

	public void insert(String word, long code, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		TrieNode curr = root;

		for (int i = 0; i < cps.length; i++) {
			int c = cps[i];
			if (curr.childArray == null) {
				curr.childArray = new TrieNode[0];
			}
			TrieNode dummy = new TrieNode(c);
			int idx = Arrays.binarySearch(curr.childArray, dummy);

			if (idx >= 0) {
				curr = curr.childArray[idx];
				if (i == cps.length - 1) {
					curr.status = 3;
					curr.code = code;
				}
			} else {
				int insertIdx = -(idx + 1);
				TrieNode[] newArr = new TrieNode[curr.childArray.length + 1];
				System.arraycopy(curr.childArray, 0, newArr, 0, insertIdx);
				System.arraycopy(curr.childArray, insertIdx, newArr, insertIdx + 1, curr.childArray.length - insertIdx);

				TrieNode newNode = new TrieNode(c, i == cps.length - 1 ? 3 : 1, i == cps.length - 1 ? code : 0);
				newArr[insertIdx] = newNode;
				curr.childArray = newArr;
				curr = newNode;
			}
		}
	}

	public long search(String word, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		TrieNode curr = root;

		for (int cp : cps) {
			if (curr.childArray == null) return -1;
			TrieNode dummy = new TrieNode(cp);
			int idx = Arrays.binarySearch(curr.childArray, dummy);
			if (idx < 0) return -1;
			curr = curr.childArray[idx];
		}
		return (curr.status == 3 || curr.status == 2) ? curr.code : -1;
	}

	public boolean delete(String word, boolean reverse) {
		String target = reverse ? new StringBuilder(word).reverse().toString() : word;
		int[] cps = target.codePoints().toArray();
		TrieNode curr = root;
		for (int cp : cps) {
			if (curr.childArray == null) return false;
			TrieNode dummy = new TrieNode(cp);
			int idx = Arrays.binarySearch(curr.childArray, dummy);
			if (idx < 0) return false;
			curr = curr.childArray[idx];
		}
		if (curr.status == 3) {
			curr.status = 1; // 简易物理降级，不触发删链，模拟性能基准
			return true;
		}
		return false;
	}

	public TrieNode getRoot() { return root; }
}