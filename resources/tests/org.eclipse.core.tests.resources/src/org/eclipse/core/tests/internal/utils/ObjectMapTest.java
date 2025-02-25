/*******************************************************************************
 *  Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Alexander Kurtakov <akurtako@redhat.com> - Bug 459343
 *******************************************************************************/
package org.eclipse.core.tests.internal.utils;

import static org.eclipse.core.tests.resources.ResourceTestUtil.createRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.internal.utils.ObjectMap;
import org.junit.Test;

public class ObjectMapTest {
	private static final int MAXIMUM = 100;

	@Test
	public void testPut() {
		// create the objects to insert into the map
		ObjectMap<Integer, Object> map = new ObjectMap<>();
		Object[] values = new Object[MAXIMUM];
		for (int i = 0; i < MAXIMUM; i++) {
			values[i] = Long.valueOf(System.currentTimeMillis());
		}

		// add each object to the map
		for (int i = 0; i < values.length; i++) {
			Integer key = Integer.valueOf(i);
			map.put(key, values[i]);
			assertTrue("2.0." + i, map.containsKey(key));
			assertTrue("2.1." + i, map.containsValue(values[i]));
			assertEquals("2.2." + i, i + 1, map.size());
		}

		// make sure they are all still there
		assertEquals("3.0", MAXIMUM, map.size());
		for (int i = 0; i < values.length; i++) {
			Integer key = Integer.valueOf(i);
			assertTrue("3.1." + i, map.containsKey(key));
			assertNotNull("3.2." + i, map.get(key));
		}
	}

	@Test
	public void testPutEmptyMap() {
		ObjectMap<Object, Object> map = new ObjectMap<>(new HashMap<>());
		map.put(new Object(), new Object());
	}

	@Test
	public void testRemove() {
		// populate the map
		Object[] values = new Object[MAXIMUM];
		ObjectMap<Integer, Object> map = populateMap(values);

		// remove each element
		for (int i = MAXIMUM - 1; i >= 0; i--) {
			Object key = Integer.valueOf(i);
			map.remove(key);
			assertTrue("2.0." + i, !map.containsKey(key));
			assertEquals("2.1," + i, i, map.size());
			// check that the others still exist
			for (int j = 0; j < i; j++) {
				assertTrue("2.2." + j, map.containsKey(Integer.valueOf(j)));
			}
		}

		// all gone?
		assertEquals("3.0", 0, map.size());
	}

	@Test
	public void testContains() {
		Object[] values = new Object[MAXIMUM];
		ObjectMap<Integer, Object> map = populateMap(values);

		for (int i = 0; i < MAXIMUM; i++) {
			assertTrue("2.0." + i, map.containsKey(Integer.valueOf(i)));
			assertTrue("2.1." + i, map.containsValue(values[i]));
		}

		assertFalse("3.0", map.containsKey(Integer.valueOf(MAXIMUM + 1)));
		assertFalse("3.1", map.containsKey(Integer.valueOf(-1)));
		assertFalse("3.2", map.containsValue(null));
		assertFalse("3.3", map.containsValue(createRandomString()));
	}

	@Test
	public void testValues() {
		Object[] values = new Object[MAXIMUM];
		ObjectMap<Integer, Object> map = populateMap(values);

		Collection<Object> result = map.values();
		for (int i = 0; i < MAXIMUM; i++) {
			assertTrue("2.0." + i, result.contains(values[i]));
		}
	}

	@Test
	public void testKeySet() {
		Object[] values = new Object[MAXIMUM];
		ObjectMap<Integer, Object> map = populateMap(values);
		Set<Integer> keys = map.keySet();
		assertEquals("1.0", MAXIMUM, keys.size());
	}

	@Test
	public void testEntrySet() {
		Object[] values = new Object[MAXIMUM];
		ObjectMap<Integer, Object> map = populateMap(values);
		Set<Map.Entry<Integer, Object>> entries = map.entrySet();
		for (int i = 0; i < MAXIMUM; i++) {
			assertTrue("1.0." + i, contains(entries, values[i]));
		}
	}

	/**
	 * The given set is a set of Map.Entry objects.
	 */
	private boolean contains(Set<Map.Entry<Integer, Object>> set, Object value) {
		for (Map.Entry<Integer, Object> entry : set) {
			if (entry.getValue().equals(value)) {
				return true;
			}
		}
		return false;
	}

	private ObjectMap<Integer, Object> populateMap(Object[] values) {
		// populate the map
		ObjectMap<Integer, Object> map = new ObjectMap<>();
		for (int i = 0; i < values.length; i++) {
			values[i] = Long.valueOf(System.currentTimeMillis());
			map.put(Integer.valueOf(i), values[i]);
		}
		assertEquals("#populateMap", values.length, map.size());
		return map;
	}

	/*
	 * Bug 62231 - empty ObjectMap.toHashMap() causes NullPointerException
	 */
	@Test
	public void testBug_62231() {
		ObjectMap<Object, Object> map = new ObjectMap<>();
		map.entrySet();
		map.clear();
		map.entrySet();

	}
}
