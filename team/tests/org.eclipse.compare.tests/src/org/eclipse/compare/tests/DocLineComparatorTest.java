/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.tests;

import java.util.HashMap;
import java.util.Optional;

import org.eclipse.compare.ICompareFilter;
import org.eclipse.compare.internal.DocLineComparator;
import org.eclipse.compare.rangedifferencer.IRangeComparator;
import org.eclipse.jface.text.*;
import org.junit.Assert;
import org.junit.Test;

public class DocLineComparatorTest {

	@Test
	public void testRangesEqual() {
		IDocument doc1 = new Document();
		doc1.set("if (s.strip))"); //$NON-NLS-1$

		IDocument doc2 = new Document();
		doc2.set("if (s.strip)"); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc1, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc2, null, true);

		Assert.assertFalse(comp1.rangesEqual(0, comp2, 0));
	}

	@Test
	public void testWhitespaceAtEnd() {
		IDocument doc1 = new Document();
		doc1.set("if (s.strip))"); //$NON-NLS-1$

		IDocument doc2 = new Document();
		doc2.set("if (s.strip))   "); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc1, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc2, null, true);

		Assert.assertTrue(comp1.rangesEqual(0, comp2, 0));
	}

	@Test
	public void testOneCompareFilter() {
		IDocument doc1 = new Document();
		doc1.set("if (s.strip))"); //$NON-NLS-1$

		IDocument doc2 = new Document();
		doc2.set("IF (S.stRIp))"); //$NON-NLS-1$

		IDocument doc3 = new Document();
		doc3.set("IF (S.stRIp))   "); //$NON-NLS-1$

		ICompareFilter filter = new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(0, 2), new Region(4, 1), new Region(8, 2) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return false;
			}
		};

		IRangeComparator comp1 = new DocLineComparator(doc1, null, false, new ICompareFilter[] { filter }, 'L', Optional.empty());
		IRangeComparator comp2 = new DocLineComparator(doc2, null, false, new ICompareFilter[] { filter }, 'R', Optional.empty());
		Assert.assertTrue(comp1.rangesEqual(0, comp2, 0));

		IRangeComparator comp3 = new DocLineComparator(doc1, null, true, new ICompareFilter[] { filter }, 'L', Optional.empty());
		IRangeComparator comp4 = new DocLineComparator(doc3, null, true, new ICompareFilter[] { filter }, 'R', Optional.empty());
		Assert.assertTrue(comp3.rangesEqual(0, comp4, 0));

		IRangeComparator comp5 = new DocLineComparator(doc1, null, false, new ICompareFilter[] { filter }, 'L', Optional.empty());
		IRangeComparator comp6 = new DocLineComparator(doc3, null, false, new ICompareFilter[] { filter }, 'R', Optional.empty());
		Assert.assertFalse(comp5.rangesEqual(0, comp6, 0));
	}

	@Test
	public void testMultipleCompareFilters() {
		IDocument doc1 = new Document();
		doc1.set("if (s.strip))"); //$NON-NLS-1$

		IDocument doc2 = new Document();
		doc2.set("IF (S.stRIp))"); //$NON-NLS-1$

		ICompareFilter filter1 = new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(0, 2) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return false;
			}
		};

		ICompareFilter filter2 = new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(4, 1) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return false;
			}
		};

		ICompareFilter filter3 = new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(8, 2) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return false;
			}
		};

		IRangeComparator comp1 = new DocLineComparator(doc1, null, false,
				new ICompareFilter[] { filter1, filter2, filter3 }, 'L', Optional.empty());
		IRangeComparator comp2 = new DocLineComparator(doc2, null, false,
				new ICompareFilter[] { filter1, filter2, filter3 }, 'R', Optional.empty());
		Assert.assertTrue(comp1.rangesEqual(0, comp2, 0));

		IRangeComparator comp3 = new DocLineComparator(doc1, null, false, new ICompareFilter[] { filter2, filter3 },
				'L', Optional.empty());
		IRangeComparator comp4 = new DocLineComparator(doc2, null, false, new ICompareFilter[] { filter2, filter3 },
				'R', Optional.empty());
		Assert.assertFalse(comp3.rangesEqual(0, comp4, 0));
	}

	@Test
	public void testWhitespace() {
		IDocument[] docs = new IDocument[6];
		docs[0] = new Document();
		docs[1] = new Document();
		docs[2] = new Document();
		docs[3] = new Document();
		docs[4] = new Document();
		docs[5] = new Document();

		docs[0].set("if (s.strip))\r\n");//$NON-NLS-1$
		docs[1].set("if (s.strip))\n"); //$NON-NLS-1$
		docs[2].set("if (s .strip))\n"); //$NON-NLS-1$
		docs[3].set("if (s.str ip))\r"); //$NON-NLS-1$
		docs[4].set("if (s.strip))"); //$NON-NLS-1$
		docs[5].set("if (s.stri p))"); //$NON-NLS-1$

		ICompareFilter[][] filters = new ICompareFilter[3][];
		filters[0] = null;
		filters[1] = new ICompareFilter[] { new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(0, 2) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return true; // cache-able
			}
		} };

		filters[2] = new ICompareFilter[] { new ICompareFilter() {

			@Override
			public void setInput(Object input, Object ancestor, Object left, Object right) {
				// EMPTY
			}

			@Override
			public IRegion[] getFilteredRegions(HashMap lineComparison) {
				return new IRegion[] { new Region(0, 2) };
			}

			@Override
			public boolean isEnabledInitially() {
				return false;
			}

			@Override
			public boolean canCacheFilteredRegions() {
				return false; // not cache-able
			}
		} };

		IRangeComparator l, r;
		for (int i = 0; i < docs.length; i++)
			for (int j = i + 1; j < docs.length; j++)
				for (ICompareFilter[] filter : filters) {
					l = new DocLineComparator(docs[i], null, false, filter, 'L', Optional.empty());
					r = new DocLineComparator(docs[j], null, false, filter, 'R', Optional.empty());
					Assert.assertFalse(l.rangesEqual(0, r, 0));
					l = new DocLineComparator(docs[i], null, true, filter, 'L', Optional.empty());
					r = new DocLineComparator(docs[j], null, true, filter, 'R', Optional.empty());
					Assert.assertTrue(l.rangesEqual(0, r, 0));
				}
	}

	@Test
	public void noWhitespaceContributorSupplied_whitespaceInStringLiteralIgnored() {
		DocLineComparator left = new DocLineComparator(new Document("str = \"Hello World\""), null, true, null, 'L', Optional.empty());
		DocLineComparator right = new DocLineComparator(new Document("str = \"HelloWorld\""), null, true, null, 'R', Optional.empty());
		Assert.assertTrue("whitespace in left document between 'Hello' and 'World' not ignored",left.rangesEqual(0, right, 0));
		Assert.assertTrue("whitespace in left document between 'Hello' and 'World' not ignored",right.rangesEqual(0, left, 0));
	}

	@Test
	public void simpleWhitespaceContributorSupplied_whitespaceInStringLiteralNotIgnored() {
		assertRangesEqualWithSimpleIgnoreWhitespaceContrbutor(//
				"DocLineComparator#rangesEqual returns unexpectedly false for two identical documents", //
				"str = \"HelloWorld\"", //
				"str = \"HelloWorld\"");

		assertRangesEqualWithSimpleIgnoreWhitespaceContrbutor(//
				"whitespaces in left document outside the string literal not ignored", //
				"str =     \"HelloWorld\"   ", //
				"str = \"HelloWorld\"");

		assertRangesNotEqualWithSimpleIngoreWhitespaceContributor(//
				"whitespace in the middle of a literal unexpectedly ignored", //
				"str = \"Hello World\"", //
				"str = \"HelloWorld\"");

		assertRangesNotEqualWithSimpleIngoreWhitespaceContributor(//
				"whitespaces in the beginning and end of a literal unexpectedly ignored", //
				"str = \" HelloWorld \"", //
				"str = \"HelloWorld\"");

		assertRangesNotEqualWithSimpleIngoreWhitespaceContributor(//
				"whitespace in the end of a literal unexpectedly ignored", //
				"str =         \" HelloWorld \"     ", //
				"str = \"HelloWorld \"");
	}

	private void assertRangesNotEqualWithSimpleIngoreWhitespaceContributor(String message, String leftSource,
			String rightSource) {
		Document leftDocument = new Document(leftSource);
		DocLineComparator left = new DocLineComparator(leftDocument, null, true, null, 'L',
				Optional.of(new SimpleIgnoreWhitespaceContributor(leftDocument)));

		Document rightDocument = new Document(rightSource);
		DocLineComparator right = new DocLineComparator(rightDocument, null, true, null, 'R',
				Optional.of(new SimpleIgnoreWhitespaceContributor(rightDocument)));
		Assert.assertFalse(message, left.rangesEqual(0, right, 0));
		Assert.assertFalse(message, right.rangesEqual(0, left, 0));
	}

	private void assertRangesEqualWithSimpleIgnoreWhitespaceContrbutor(String message, String leftSource,
			String rightSource) {
		Document leftDocument = new Document(leftSource);
		DocLineComparator left = new DocLineComparator(leftDocument, null, true, null, 'L',
				Optional.of(new SimpleIgnoreWhitespaceContributor(leftDocument)));

		Document rightDocument = new Document(rightSource);
		DocLineComparator right = new DocLineComparator(rightDocument, null, true, null, 'R',
				Optional.of(new SimpleIgnoreWhitespaceContributor(rightDocument)));
		Assert.assertTrue(message, left.rangesEqual(0, right, 0));
		Assert.assertTrue(message, right.rangesEqual(0, left, 0));
	}

	@Test
	public void testEmpty() {
		IDocument doc1 = new Document();
		doc1.set(""); //$NON-NLS-1$

		IDocument doc2 = new Document();
		doc2.set("    "); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc1, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc2, null, true);

		Assert.assertTrue(comp1.rangesEqual(0, comp2, 0));
	}

	@Test
	public void testNoContent() {
		IDocument doc = new Document();

		IRangeComparator comp1 = new DocLineComparator(doc, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc, new Region(0, doc.getLength()), true);

		Assert.assertTrue(comp1.rangesEqual(0, comp2, 0));
		Assert.assertEquals(comp1.getRangeCount(), comp2.getRangeCount());
		Assert.assertEquals(1, comp2.getRangeCount());
	}

	@Test
	public void testOneLine() {
		IDocument doc = new Document();
		doc.set("line1"); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc, new Region(0, doc.getLength()), true);

		Assert.assertEquals(comp1.getRangeCount(), comp2.getRangeCount());
		Assert.assertEquals(1, comp2.getRangeCount());
	}

	@Test
	public void testTwoLines() {
		IDocument doc = new Document();
		doc.set("line1\nline2"); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc, new Region(0, doc.getLength()), true);

		Assert.assertEquals(comp1.getRangeCount(), comp2.getRangeCount());
		Assert.assertEquals(2, comp2.getRangeCount());

		IRangeComparator comp3 = new DocLineComparator(doc, new Region(0, "line1".length()), true);
		Assert.assertEquals(1, comp3.getRangeCount());

		comp3 = new DocLineComparator(doc, new Region(0, "line1".length() + 1), true);
		Assert.assertEquals(2, comp3.getRangeCount()); // two lines
	}

	@Test
	public void testBug259422() {
		IDocument doc = new Document();
		doc.set(""); //$NON-NLS-1$

		IRangeComparator comp1 = new DocLineComparator(doc, null, true);
		IRangeComparator comp2 = new DocLineComparator(doc, new Region(0, doc.getLength()), true);

		Assert.assertEquals(comp1.getRangeCount(), comp2.getRangeCount());
	}

}
