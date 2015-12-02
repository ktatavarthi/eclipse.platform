/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ua.tests.help.criteria;

import junit.framework.TestCase;

import org.eclipse.help.ICriteria;
import org.eclipse.help.internal.Topic;
import org.eclipse.help.internal.base.scope.CriteriaHelpScope;
import org.eclipse.help.internal.criteria.CriteriaProviderRegistry;
import org.eclipse.help.internal.criteria.CriterionResource;
import org.eclipse.help.internal.toc.Toc;
import org.eclipse.ua.tests.help.other.UserCriteria;
import org.eclipse.ua.tests.help.other.UserToc2;
import org.eclipse.ua.tests.help.other.UserTopic2;

public class TestCriteriaProvider extends TestCase {

	public void testUserTopicWithCriteria() throws Exception {
		UserTopic2 topic = new UserTopic2("Topic", null, true);
		UserCriteria criterion1 = new UserCriteria("version", "1.0", true);
		UserCriteria criterion2 = new UserCriteria("version", "2.0", true);
		topic.addCriterion(criterion1);
		topic.addCriterion(criterion2);
		
		Topic copy = new Topic(topic);
		
		ICriteria[] nativeCriteria = copy.getCriteria();
	    assertEquals(2, nativeCriteria.length);
	    assertEquals("version", nativeCriteria[0].getName());
	    assertEquals("1.0", nativeCriteria[0].getValue());
	    assertEquals("version", nativeCriteria[1].getName());
	    assertEquals("2.0", nativeCriteria[1].getValue());	    
	    
	    ICriteria[] allCriteria = CriteriaProviderRegistry.getInstance().getAllCriteria(copy);
	    assertTrue(containsCriterion(allCriteria, "version", "2.0"));
	    assertTrue(containsCriterion(allCriteria, "version", "1.0"));
	    assertTrue(containsCriterion(allCriteria, "containsLetter", "c"));
	    assertFalse(containsCriterion(allCriteria, "containsLetter", "k"));    
	}
	
	public void testUserTocWithCriteria() throws Exception {
		UserToc2 toc = new UserToc2("Toc", null, true);
		UserCriteria criterion1 = new UserCriteria("version", "1.0", true);
		UserCriteria criterion2 = new UserCriteria("version", "2.0", true);
		toc.addCriterion(criterion1);
		toc.addCriterion(criterion2);
		
		Toc copy = new Toc(toc);
		
		ICriteria[] nativeCriteria = copy.getCriteria();
	    assertEquals(2, nativeCriteria.length);
	    assertEquals("version", nativeCriteria[0].getName());
	    assertEquals("1.0", nativeCriteria[0].getValue());
	    assertEquals("version", nativeCriteria[1].getName());
	    assertEquals("2.0", nativeCriteria[1].getValue());	    
	    
	    ICriteria[] allCriteria = CriteriaProviderRegistry.getInstance().getAllCriteria(copy);
	    assertTrue(containsCriterion(allCriteria, "version", "2.0"));
	    assertTrue(containsCriterion(allCriteria, "version", "1.0"));
	    assertTrue(containsCriterion(allCriteria, "containsLetter", "c"));
	    assertFalse(containsCriterion(allCriteria, "containsLetter", "k"));    
	}

	public void testCriteriaScope() throws Exception {
		UserTopic2 topic = new UserTopic2("Topic", null, true);
		UserCriteria criterion1 = new UserCriteria("version", "1.0", true);
		topic.addCriterion(criterion1);
		CriterionResource resourceC = new CriterionResource("containsletter");
		resourceC.addCriterionValue("c");
		CriteriaHelpScope scopeC = new CriteriaHelpScope(new CriterionResource[] { resourceC });
		assertTrue(scopeC.inScope(topic));
		CriterionResource resourceK = new CriterionResource("containsletter");
		resourceK.addCriterionValue("k");
		CriteriaHelpScope scopeK = new CriteriaHelpScope(new CriterionResource[] { resourceK });
		assertFalse(scopeK.inScope(topic));
	}
	

	//public void testWorkingSetScope() throws Exception {
		// TODO write a test  which creates a working set scope based on the
	    // criteria which are generated by the criteria provider
	//}

	private boolean containsCriterion(ICriteria[] allCriteria,
			String name, String value) {
        for (ICriteria element : allCriteria) {
        	if (element.getName().equals(name) && element.getValue().equals(value)) {
        		return true;
        	}
        }
    	return false;
	}
	
}
