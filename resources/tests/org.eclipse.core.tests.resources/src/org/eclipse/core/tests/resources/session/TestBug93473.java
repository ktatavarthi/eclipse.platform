/*******************************************************************************
 * Copyright (c) 2005, 2012 IBM Corporation and others.
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
package org.eclipse.core.tests.resources.session;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestPluginConstants.PI_RESOURCES_TESTS;
import static org.eclipse.core.tests.resources.ResourceTestUtil.assertDoesNotExistInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createRandomString;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;

import junit.framework.Test;
import org.eclipse.core.internal.resources.ContentDescriptionManager;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.tests.resources.ContentDescriptionManagerTest;
import org.eclipse.core.tests.resources.WorkspaceSessionTest;
import org.eclipse.core.tests.session.WorkspaceSessionTestSuite;

/**
 * Tests that the content description cache is preserved across sessions.
 *
 * Note that this test is sensitive to the platform state stamp.  If the test
 * starts failing, it might mean bundles are being re-installed unnecessarily
 * in the second session.  For details, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=94859.
 * @since 3.2
 */
public class TestBug93473 extends WorkspaceSessionTest {

	public static Test suite() {
		return new WorkspaceSessionTestSuite(PI_RESOURCES_TESTS, TestBug93473.class);
	}

	public void test1stSession() throws CoreException {
		final IWorkspace workspace = getWorkspace();

		// cache is invalid at this point (does not match platform timestamp), no flush job has been scheduled (should not have to wait)
		ContentDescriptionManagerTest.waitForCacheFlush();
		assertEquals("0.0", ContentDescriptionManager.INVALID_CACHE, ((Workspace) workspace).getContentDescriptionManager().getCacheState());

		IProject project = workspace.getRoot().getProject("proj1");
		assertDoesNotExistInWorkspace(project);
		Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
		IFile file = project.getFile("foo.txt");
		assertDoesNotExistInWorkspace(file);
		createInWorkspace(file, createRandomString());
		// this will also cause the cache flush job to be scheduled
		file.getContentDescription();
		// after waiting cache flushing, cache should be new
		ContentDescriptionManagerTest.waitForCacheFlush();
		assertEquals("2.0", ContentDescriptionManager.EMPTY_CACHE, ((Workspace) workspace).getContentDescriptionManager().getCacheState());

		// obtains a content description again - should come from cache
		file.getContentDescription();
		// cache now is not empty anymore (should not have to wait)
		ContentDescriptionManagerTest.waitForCacheFlush();
		assertEquals("4.0", ContentDescriptionManager.USED_CACHE, ((Workspace) workspace).getContentDescriptionManager().getCacheState());

		workspace.save(true, createTestMonitor());
	}

	public void test2ndSession() {
		// cache should preserve state across sessions
		assertEquals("1.0", ContentDescriptionManager.USED_CACHE, ((Workspace) getWorkspace()).getContentDescriptionManager().getCacheState());
	}

}
