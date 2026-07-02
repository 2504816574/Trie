package cn.com;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressedTrieTests {

	@Test
	void deletingLeafPreservesSiblingBranch() {
		CompressedTrie trie = new CompressedTrie();
		trie.insert("abc", 1, false);
		trie.insert("abd", 2, false);

		assertTrue(trie.delete("abc", false));
		assertEquals(-1, trie.search("abc", false));
		assertEquals(2, trie.search("abd", false));
	}

	@Test
	void deletingPrefixPreservesLongerWord() {
		CompressedTrie trie = new CompressedTrie();
		trie.insert("ab", 1, false);
		trie.insert("abc", 2, false);

		assertTrue(trie.delete("ab", false));
		assertEquals(-1, trie.search("ab", false));
		assertEquals(2, trie.search("abc", false));
	}

	@Test
	void deleteReportsWhetherWordExisted() {
		CompressedTrie trie = new CompressedTrie();
		trie.insert("abc", 1, false);

		assertFalse(trie.delete("missing", false));
		assertTrue(trie.delete("abc", false));
		assertFalse(trie.delete("abc", false));
	}

	@Test
	void reverseDeletePreservesSiblingBranch() {
		CompressedTrie trie = new CompressedTrie();
		trie.insert("甲公司", 1, true);
		trie.insert("乙公司", 2, true);

		assertTrue(trie.delete("甲公司", true));
		assertEquals(-1, trie.search("甲公司", true));
		assertEquals(2, trie.search("乙公司", true));
	}

	@Test
	void partitionedScanCombinesMatchesFromEveryPartition() {
		CompressedTrie[] tries = {new CompressedTrie(), new CompressedTrie(), new CompressedTrie()};
		tries[0].insert("甲公司", 10, false);
		tries[1].insert("乙公司", 11, false);
		tries[2].insert("丙公司", 12, false);

		List<MatchResult> matches =
				TrieBenchmarkRunner.scanPartitionedCompressed(tries, "甲公司与乙公司", false);

		assertEquals(2, matches.size());
		assertTrue(matches.stream().anyMatch(m -> m.companyCode == 10));
		assertTrue(matches.stream().anyMatch(m -> m.companyCode == 11));
	}
}
