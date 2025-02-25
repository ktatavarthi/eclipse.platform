/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
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
 *     Alexander Kurtakov <akurtako@redhat.com> - Bug 459343
 *******************************************************************************/
package org.eclipse.core.tests.resources.session;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestPluginConstants.PI_RESOURCES_TESTS;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createRandomContentsStream;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;

import java.util.Arrays;
import java.util.List;
import junit.framework.Test;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.resources.WorkspaceSessionTest;
import org.eclipse.core.tests.session.WorkspaceSessionTestSuite;

/**
 * Copies the tests from HistoryStoreTest#testFindDeleted, phrased
 * as a session test.
 */
public class FindDeletedMembersTest extends WorkspaceSessionTest {
	//common objects
	protected IWorkspaceRoot root;
	protected IProject project;
	protected IFile pfile;
	protected IFile folderAsFile;
	protected IFolder folder;
	protected IFile file;
	protected IFile file1;
	protected IFile file2;
	protected IFolder folder2;
	protected IFile file3;

	@Override
	protected void setUp() throws Exception {
		root = getWorkspace().getRoot();
		project = root.getProject("MyProject");
		pfile = project.getFile("file.txt");
		folder = project.getFolder("folder");
		file = folder.getFile("file.txt");
		folderAsFile = project.getFile(folder.getProjectRelativePath());
		file1 = folder.getFile("file1.txt");
		file2 = folder.getFile("file2.txt");
		folder2 = folder.getFolder("folder2");
		file3 = folder2.getFile("file3.txt");

	}

	private void saveWorkspace() throws CoreException {
		getWorkspace().save(true, createTestMonitor());
	}

	public void test1() throws Exception {
		project.create(createTestMonitor());
		project.open(createTestMonitor());

		IFile[] df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.1", 0, df.length);

		// test that a deleted file can be found
		// create and delete a file
		pfile.create(createRandomContentsStream(), true, createTestMonitor());
		pfile.delete(true, true, createTestMonitor());

		saveWorkspace();
	}

	public void test2() throws Exception {
		// the deleted file should show up as a deleted member of project
		IFile[] df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.1", 1, df.length);
		assertEquals("0.2", pfile, df[0]);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("0.3", 1, df.length);
		assertEquals("0.4", pfile, df[0]);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("0.5", 0, df.length);

		// the deleted file should show up as a deleted member of workspace root
		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.5.1", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("0.5.2", 1, df.length);
		assertEquals("0.5.3", pfile, df[0]);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("0.5.4", 0, df.length);

		// recreate the file
		pfile.create(createRandomContentsStream(), true, createTestMonitor());

		saveWorkspace();
	}

	public void test3() throws Exception {
		// the deleted file should no longer show up as a deleted member of project
		IFile[] df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.6", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("0.7", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("0.8", 0, df.length);

		// the deleted file should no longer show up as a deleted member of ws root
		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.8.1", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("0.8.2", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("0.8.3", 0, df.length);

		// scrub the project
		project.delete(true, createTestMonitor());
		project.create(createTestMonitor());
		project.open(createTestMonitor());

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("0.9", 0, df.length);

		// test folder
		// create and delete a file in a folder
		folder.create(true, true, createTestMonitor());
		file.create(createRandomContentsStream(), true, createTestMonitor());
		file.delete(true, true, createTestMonitor());

		saveWorkspace();
	}

	public void test4() throws Exception {
		// the deleted file should show up as a deleted member
		IFile[] df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("1.1", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("1.2", 1, df.length);
		assertEquals("1.3", file, df[0]);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("1.4", 0, df.length);

		// recreate the file
		file.create(createRandomContentsStream(), true, createTestMonitor());

		// the recreated file should no longer show up as a deleted member
		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("1.5", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("1.6", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("1.7", 0, df.length);

		// deleting the folder should bring it back into history
		folder.delete(true, true, createTestMonitor());

		saveWorkspace();
	}

	public void test5() throws Exception {
		// the deleted file should show up as a deleted member of project
		IFile[] df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("1.8", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("1.9", 1, df.length);
		assertEquals("1.10", file, df[0]);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("1.11", 0, df.length);

		// create and delete a file where the folder was
		folderAsFile.create(createRandomContentsStream(), true, createTestMonitor());
		folderAsFile.delete(true, true, createTestMonitor());
		folder.create(true, true, createTestMonitor());

		// the deleted file should show up as a deleted member of folder
		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("1.12", 1, df.length);
		assertEquals("1.13", folderAsFile, df[0]);

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("1.14", 2, df.length);
		List<IFile> dfList = Arrays.asList(df);
		assertTrue("1.15", dfList.contains(file));
		assertTrue("1.16", dfList.contains(folderAsFile));

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("1.17", 2, df.length);
		dfList = Arrays.asList(df);
		assertTrue("1.18", dfList.contains(file));
		assertTrue("1.19", dfList.contains(folderAsFile));

		// scrub the project
		project.delete(true, createTestMonitor());
		project.create(createTestMonitor());
		project.open(createTestMonitor());

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("1.50", 0, df.length);

		// test a bunch of deletes
		// create and delete a file in a folder
		folder.create(true, true, createTestMonitor());
		folder2.create(true, true, createTestMonitor());
		file1.create(createRandomContentsStream(), true, createTestMonitor());
		file2.create(createRandomContentsStream(), true, createTestMonitor());
		file3.create(createRandomContentsStream(), true, createTestMonitor());
		folder.delete(true, true, createTestMonitor());

		saveWorkspace();
	}

	public void test6() throws Exception {
		// under root
		IFile[] df = root.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("3.1", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("3.2", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("3.3", 3, df.length);
		List<IFile> dfList = Arrays.asList(df);
		assertTrue("3.3.1", dfList.contains(file1));
		assertTrue("3.3.2", dfList.contains(file2));
		assertTrue("3.3.3", dfList.contains(file3));

		// under project
		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("3.4", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("3.5", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("3.6", 3, df.length);
		dfList = Arrays.asList(df);
		assertTrue("3.6.1", dfList.contains(file1));
		assertTrue("3.6.2", dfList.contains(file2));
		assertTrue("3.6.3", dfList.contains(file3));

		// under folder
		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("3.7", 0, df.length);

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("3.8", 2, df.length);

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("3.9", 3, df.length);

		// under folder2
		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("3.10", 0, df.length);

		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("3.11", 1, df.length);

		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("3.12", 1, df.length);

		project.delete(true, createTestMonitor());

		saveWorkspace();
	}

	public void test7() throws Exception {
		// once the project is gone, so is all the history for that project
		// under root
		IFile[] df = root.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("4.1", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("4.2", 0, df.length);

		df = root.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("4.3", 0, df.length);

		// under project
		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("4.4", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("4.5", 0, df.length);

		df = project.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("4.6", 0, df.length);

		// under folder
		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("4.7", 0, df.length);

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("4.8", 0, df.length);

		df = folder.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("4.9", 0, df.length);

		// under folder2
		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_ZERO, createTestMonitor());
		assertEquals("4.10", 0, df.length);

		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_ONE, createTestMonitor());
		assertEquals("4.11", 0, df.length);

		df = folder2.findDeletedMembersWithHistory(IResource.DEPTH_INFINITE, createTestMonitor());
		assertEquals("4.12", 0, df.length);

		saveWorkspace();
	}

	public static Test suite() {
		return new WorkspaceSessionTestSuite(PI_RESOURCES_TESTS, FindDeletedMembersTest.class);
	}
}
