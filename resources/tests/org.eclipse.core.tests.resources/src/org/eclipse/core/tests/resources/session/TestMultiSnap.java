/*******************************************************************************
 *  Copyright (c) 2000, 2012 IBM Corporation and others.
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
 *******************************************************************************/
package org.eclipse.core.tests.resources.session;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestPluginConstants.PI_RESOURCES_TESTS;
import static org.eclipse.core.tests.resources.ResourceTestUtil.assertExistsInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;

import junit.framework.Test;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.session.WorkspaceSessionTestSuite;

/**
 * Tests performing multiple snapshots on a workspace that has
 * never been saved, then crashing and recovering.
 */
public class TestMultiSnap extends WorkspaceSerializationTest {

	public void test1() throws Exception {

		/* create some resource handles */
		IProject project = getWorkspace().getRoot().getProject(PROJECT);
		project.create(createTestMonitor());
		project.open(createTestMonitor());

		/* snapshot */
		workspace.save(false, createTestMonitor());

		/* do more stuff */
		IFolder folder = project.getFolder(FOLDER);
		folder.create(true, true, createTestMonitor());

		workspace.save(false, createTestMonitor());

		/* do even more stuff */
		IFile file = folder.getFile(FILE);
		byte[] bytes = "Test bytes".getBytes();
		java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes);
		file.create(in, true, createTestMonitor());

		workspace.save(false, createTestMonitor());

		//exit without saving
	}

	public void test2() throws CoreException {
		IProject project = getWorkspace().getRoot().getProject(PROJECT);
		IFolder folder = project.getFolder(FOLDER);
		IFile file = folder.getFile(FILE);

		/* see if the workspace contains the resources created earlier*/
		IResource[] children = getWorkspace().getRoot().members();
		assertEquals("1.0", 1, children.length);
		assertEquals("1.1", children[0], project);
		assertTrue("1.2", project.exists());
		assertTrue("1.3", project.isOpen());

		assertExistsInWorkspace(new IResource[] { project, folder, file });
	}

	public static Test suite() {
		return new WorkspaceSessionTestSuite(PI_RESOURCES_TESTS, TestMultiSnap.class);
	}
}
