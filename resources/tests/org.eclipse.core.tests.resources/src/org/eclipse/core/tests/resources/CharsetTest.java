/*******************************************************************************
 *  Copyright (c) 2004, 2022 IBM Corporation and others.
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
 *     James Blackburn (Broadcom Corp.) - ongoing development
 *     Sergey Prigogin (Google) - [464072] Refresh on Access ignored during text search
 *******************************************************************************/
package org.eclipse.core.tests.resources;

import static org.eclipse.core.resources.ResourcesPlugin.PI_RESOURCES;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.assertDoesNotExistInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.assertExistsInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInFileSystem;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createInWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createRandomString;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createTestMonitor;
import static org.eclipse.core.tests.resources.ResourceTestUtil.createUniqueString;
import static org.eclipse.core.tests.resources.ResourceTestUtil.ensureOutOfSync;
import static org.eclipse.core.tests.resources.ResourceTestUtil.removeFromWorkspace;
import static org.eclipse.core.tests.resources.ResourceTestUtil.touchInFilesystem;
import static org.eclipse.core.tests.resources.ResourceTestUtil.waitForEncodingRelatedJobs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.core.internal.events.NotificationManager;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.internal.resources.CharsetDeltaJob;
import org.eclipse.core.internal.resources.CharsetManager;
import org.eclipse.core.internal.resources.ValidateProjectEncoding;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.osgi.service.prefs.BackingStoreException;

public class CharsetTest {

	@Rule
	public TestName testName = new TestName();

	@Rule
	public WorkspaceTestRule workspaceRule = new WorkspaceTestRule();

	private static final class JobChangeAdapterExtension extends JobChangeAdapter {
		private IStatus result;

		public JobChangeAdapterExtension() {
		}

		@Override
		public void done(IJobChangeEvent event) {
			if (event.getJob().belongsTo(CharsetDeltaJob.FAMILY_CHARSET_DELTA)) {
				this.result = event.getResult();
			}
		}

		IStatus getResult() {
			return this.result;
		}
	}

	public class CharsetVerifier extends ResourceDeltaVerifier {
		static final int IGNORE_BACKGROUND_THREAD = 0x02;
		static final int IGNORE_CREATION_THREAD = 0x01;
		private final Thread creationThread = Thread.currentThread();
		private final int flags;

		CharsetVerifier(int flags) {
			this.flags = flags;
		}

		@Override
		void internalVerifyDelta(IResourceDelta delta) {
			// do NOT ignore any changes to project preferences only to .project
			IPath path = delta.getFullPath();
			if (path.segmentCount() == 2 && path.segment(1).equals(".project")) {
				return;
			}
			super.internalVerifyDelta(delta);
		}

		private boolean isSet(int mask) {
			return (mask & flags) == mask;
		}

		boolean ignoreEvent() {
			// to make testing easier, we allow events from the main or other thread to be ignored
			if (isSet(IGNORE_BACKGROUND_THREAD) && Thread.currentThread() != creationThread) {
				return true;
			}
			if (isSet(IGNORE_CREATION_THREAD) && Thread.currentThread() == creationThread) {
				return true;
			}
			return false;
		}

		@Override
		public synchronized void resourceChanged(IResourceChangeEvent e) {
			if (ignoreEvent()) {
				return;
			}
			super.resourceChanged(e);
			this.notify();
		}

		public synchronized boolean waitForEvent(long limit) {
			Job.getJobManager().wakeUp(CharsetManager.class);
			for (int i = 1; i < limit; i++) {
				if (hasBeenNotified()) {
					return true;
				}
				try {
					this.wait(1);
				} catch (InterruptedException e) {
					// ignore
				}
			}
			return hasBeenNotified();
		}
	}

	static final String SAMPLE_SPECIFIC_XML = "<?xml version=\"1.0\"?><org.eclipse.core.tests.resources.anotherXML/>";
	private static final String SAMPLE_XML_DEFAULT_ENCODING = "<?xml version=\"1.0\"?><org.eclipse.core.resources.tests.root/>";
	static final String SAMPLE_XML_ISO_8859_1_ENCODING = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><org.eclipse.core.resources.tests.root/>";
	static final String SAMPLE_XML_US_ASCII_ENCODING = "<?xml version=\"1.0\" encoding=\"US-ASCII\"?><org.eclipse.core.resources.tests.root/>";

	static final String SAMPLE_DERIVED_ENCODING_TO_FALSE_REGULAR_PREFS = "#Mon Nov 15 17:54:11 CET 2010\n"
			+ ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS + "=false\neclipse.preferences.version=1";
	static final String[] SAMPLE_DERIVED_ENCODING_AFTER_FALSE_REGULAR_PREFS = {
			ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS + "=false", "encoding//b1/a.txt=UTF-8",
			"eclipse.preferences.version=1" };

	static final String SAMPLE_DERIVED_ENCODING_TO_TRUE_REGULAR_PREFS = "#Mon Nov 15 17:54:11 CET 2010\n"
			+ ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS
			+ "=true\nencoding//b1/a.txt=UTF-8\neclipse.preferences.version=1";
	static final String[] SAMPLE_DERIVED_ENCODING_AFTER_TRUE_REGULAR_PREFS = {
			ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS + "=true", "eclipse.preferences.version=1" };
	static final String[] SAMPLE_DERIVED_ENCODING_AFTER_TRUE_DERIVED_PREFS = { "encoding//b1/a.txt=UTF-8",
			"eclipse.preferences.version=1" };

	private String savedWorkspaceCharset;

	@Test
	@Ignore("disabled due to bug 67606")
	public void testBug67606() throws CoreException {
		IWorkspace workspace = getWorkspace();
		final IProject project = workspace.getRoot().getProject("MyProject");
		try {
			final IFile file = project.getFile("file.txt");
			createInWorkspace(file);
			project.setDefaultCharset("FOO", createTestMonitor());
			workspace.run((IWorkspaceRunnable) monitor -> {
				assertEquals("0.9", "FOO", file.getCharset());
				file.setCharset("BAR", createTestMonitor());
				assertEquals("1.0", "BAR", file.getCharset());
				file.move(project.getFullPath().append("file2.txt"), IResource.NONE, monitor);
				IFile file2 = project.getFile("file2.txt");
				assertExistsInWorkspace(file2);
				assertEquals("2.0", "BAR", file.getCharset());
			}, null);
		} finally {
			removeFromWorkspace(project);
		}
	}

	@Test
	public void testCharsetMoveOnFileMove() throws CoreException {
		IWorkspace workspace = getWorkspace();
		final IProject project = workspace.getRoot().getProject("MyProject");
		try {
			final IFile file = project.getFile("file.txt");
			createInWorkspace(file);
			project.setDefaultCharset("FOO", createTestMonitor());
			assertEquals("Setting up Projects default charset was successful", "FOO", file.getCharset());
			file.setCharset("BAR", createTestMonitor());
			assertEquals("Setting up file's explicit charset was successful", "BAR", file.getCharset());
			file.move(project.getFullPath().append("file2.txt"), IResource.NONE, createTestMonitor());
			IFile file2 = project.getFile("file2.txt");
			assertExistsInWorkspace(file2);
			assertEquals("The file's charset was correctly copied while coying the file", "BAR", file2.getCharset());
		} finally {
			removeFromWorkspace(project);
		}
	}

	@Test
	@Ignore("https://github.com/eclipse-platform/eclipse.platform/issues/634")
	public void testCopyFileCopiesCharset() throws CoreException {
		IWorkspace workspace = getWorkspace();
		final IProject project = workspace.getRoot().getProject("MyProject");
		try {
			project.create(createTestMonitor());
			project.open(createTestMonitor());
			final IFile file = project.getFile("file.txt");
			createInWorkspace(file);
			file.setCharset("FOO", createTestMonitor());
			assertEquals("File charset correctly set", file.getCharset(true), "FOO");
			file.copy(project.getFullPath().append("file2.txt"), IResource.NONE, createTestMonitor());
			final IFile copiedFile = project.getFile("file2.txt");
			assertEquals("File with explicitly set charset keeps charset", copiedFile.getCharset(true), "FOO");
		} finally {
			removeFromWorkspace(project);
		}
	}

	/**
	 * Asserts that the given resources have the given [default] charset.
	 */
	private void assertCharsetIs(String tag, String encoding, IResource[] resources, boolean checkImplicit) throws CoreException {
		for (IResource resource : resources) {
			String resourceCharset = resource instanceof IFile f ? f.getCharset(checkImplicit)
					: ((IContainer) resource).getDefaultCharset(checkImplicit);
			assertEquals(tag + " " + resource.getFullPath(), encoding, resourceCharset);
		}
	}

	private void clearAllEncodings(IResource root) throws CoreException {
		if (root == null || !root.exists()) {
			return;
		}
		IResourceVisitor visitor = resource -> {
			if (!resource.exists()) {
				return false;
			}
			switch (resource.getType()) {
				case IResource.FILE :
					((IFile) resource).setCharset(null, createTestMonitor());
					break;
				case IResource.ROOT :
					// do nothing
					break;
				default :
					((IContainer) resource).setDefaultCharset(null, createTestMonitor());
			}
			return true;
		};
		root.accept(visitor);
		waitForEncodingRelatedJobs(testName.getMethodName());
	}

	private IFile getResourcesPreferenceFile(IProject project, boolean forDerivedResources) {
		if (forDerivedResources) {
			return project.getFolder(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME)
					.getFile(PI_RESOURCES + ".derived." + EclipsePreferences.PREFS_FILE_EXTENSION);
		}
		return project.getFolder(EclipsePreferences.DEFAULT_PREFERENCES_DIRNAME)
				.getFile(PI_RESOURCES + "." + EclipsePreferences.PREFS_FILE_EXTENSION);
	}

	private Reader getTextContents(String text) {
		return new StringReader(text);
	}

	private boolean isDerivedEncodingStoredSeparately(IProject project) {
		org.osgi.service.prefs.Preferences node = Platform.getPreferencesService().getRootNode().node(ProjectScope.SCOPE);
		String projectName = project.getName();
		try {
			if (!node.nodeExists(projectName)) {
				return false;
			}
			node = node.node(projectName);
			if (!node.nodeExists(PI_RESOURCES)) {
				return false;
			}
			node = node.node(PI_RESOURCES);
			return node.getBoolean(ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS, false);
		} catch (BackingStoreException e) {
			// default value
			return false;
		}
	}

	private void setDerivedEncodingStoredSeparately(IProject project, boolean value)
			throws BackingStoreException {
		org.osgi.service.prefs.Preferences prefs = new ProjectScope(project).getNode(PI_RESOURCES);
		if (!value) {
			prefs.remove(ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS);
		} else {
			prefs.putBoolean(ResourcesPlugin.PREF_SEPARATE_DERIVED_ENCODINGS, true);
		}
		prefs.flush();
	}

	private static IEclipsePreferences getResourcesPreferences() {
		return InstanceScope.INSTANCE.getNode(PI_RESOURCES);
	}

	@Before
	public void setUp() throws Exception {
		// save the workspace charset so it can be restored after the test
		savedWorkspaceCharset = getResourcesPreferences().get(ResourcesPlugin.PREF_ENCODING, "");
	}

	@After
	public void tearDown() throws Exception {
		// restore the workspace charset
		getResourcesPreferences().put(ResourcesPlugin.PREF_ENCODING, savedWorkspaceCharset);
		// Reset the PREF_LIGHTWEIGHT_AUTO_REFRESH preference to its default value.
		getResourcesPreferences().remove(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH);
		waitForEncodingRelatedJobs(testName.getMethodName());
	}

	@Test
	public void testBug59899() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(createUniqueString());
		try {
			IFile file = project.getFile("file.txt");
			IFolder folder = project.getFolder("folder");
			createInWorkspace(new IResource[] {file, folder});
			file.setCharset("FOO", createTestMonitor());
			folder.setDefaultCharset("BAR", createTestMonitor());
			project.setDefaultCharset("PROJECT_CHARSET", createTestMonitor());
			getWorkspace().getRoot().setDefaultCharset("ROOT_CHARSET", createTestMonitor());
		} finally {
			clearAllEncodings(project);
		}
	}

	@Test
	public void testBug62732() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		IContentType anotherXML = contentTypeManager.getContentType("org.eclipse.core.tests.resources.anotherXML");
		assertNotNull("0.5", anotherXML);
		createInWorkspace(project);
		IFile file = project.getFile("file.xml");
		createInWorkspace(file, SAMPLE_SPECIFIC_XML);
		IContentDescription description = file.getContentDescription();
		assertNotNull("1.0", description);
		assertEquals("1.1", anotherXML, description.getContentType());
		description = file.getContentDescription();
		assertNotNull("2.0", description);
		assertEquals("2.1", anotherXML, description.getContentType());
	}

	@Test
	public void testBug64503() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		IContentType text = contentTypeManager.getContentType("org.eclipse.core.runtime.text");
		IFile file = project.getFile("file.txt");
		createInWorkspace(file);
		IContentDescription description = file.getContentDescription();
		assertNotNull("1.0", description);
		assertEquals("1.1", text, description.getContentType());
		removeFromWorkspace(file);
		CoreException e = assertThrows(CoreException.class, file::getContentDescription);
		// Ok, the resource does not exist.
		assertEquals("1.3", IResourceStatus.RESOURCE_NOT_FOUND, e.getStatus().getCode());
	}

	@Test
	public void testBug94279() throws CoreException {
		final IWorkspaceRoot root = getWorkspace().getRoot();
		String originalUserCharset = root.getDefaultCharset(false);
		try {
			root.setDefaultCharset(null, null);
			assertNull("1.0", root.getDefaultCharset(false));

			root.setDefaultCharset(null, new NullProgressMonitor());
			assertNull("1.0", root.getDefaultCharset(false));
		} finally {
			if (originalUserCharset != null) {
				root.setDefaultCharset(originalUserCharset, null);
			}
		}
	}

	@Test
	public void testBug333056() throws Exception {
		IProject project = null;
		try {
			IWorkspace workspace = getWorkspace();
			project = workspace.getRoot().getProject("MyProject");
			createInWorkspace(project);
			project.setDefaultCharset("BAR", createTestMonitor());

			IFolder folder = project.getFolder(createUniqueString());
			IFile file = folder.getFile(createUniqueString());
			assertEquals("1.0", "BAR", file.getCharset(true));

			createInWorkspace(folder);
			assertEquals("2.0", "BAR", file.getCharset(true));

			folder.setDerived(true, createTestMonitor());
			assertEquals("3.0", "BAR", file.getCharset(true));

			setDerivedEncodingStoredSeparately(project, true);
			assertEquals("5.0", "BAR", file.getCharset(true));
		} finally {
			clearAllEncodings(project);
		}
	}

	/**
	 * Test for getting charset on an IFile:
	 * #getContentDescription() checks file sync state(), always returning the
	 * correct content description, whereas getCharset() uses the cached charset if available.
	 */
	@Test
	public void testBug186984() throws Exception {
		getResourcesPreferences().putBoolean(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH, false);
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(createUniqueString());
		IFile file = project.getFile("file.xml");

		// Test changing content types externally as per bug 186984 Comment 8
		String ascii = "<?xml version=\"1.0\" encoding=\"ascii\"?>";
		String utf = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

		// test that we can get the charset, when the file doesn't exist in a file system
		file.getCharset(true);

		// test that we can get the charset, when the file is out-of-sync
		createInWorkspace(file);
		assertTrue(file.getLocation().toFile().delete());
		file.getCharset(true);

		createInFileSystem(file);
		ensureOutOfSync(file);
		//getCharset uses a cached value, so it will still pass
		file.getCharset(true);

		// set the content type within the XML file, ensure that #getContentDescription (which respects sync state)
		// returns the correct value.

		// 1) first set the content type to ascii
		file.setContents(new ByteArrayInputStream(ascii.getBytes("ascii")), IResource.FORCE, createTestMonitor());
		assertTrue("4.0", file.getCharset().equals("ascii"));
		assertTrue("4.1", file.getContentDescription().getCharset().equals("ascii"));

		// 2) Make out of sync - Methods should still work, giving the previous value
		touchInFilesystem(file);
		assertTrue("4.2", file.getCharset().equals("ascii"));
		CoreException e = assertThrows(CoreException.class,
				() -> file.getContentDescription().getCharset().equals("ascii"));
		assertEquals("the file should be out of sync", IResourceStatus.OUT_OF_SYNC_LOCAL, e.getStatus().getCode());

		// As we now know that #getContentDescription correctly checks sync state, just enable LIGHTWEIGHT refresh
		// for the rest of the test.
		getResourcesPreferences().putBoolean(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH, true);
		assertTrue("4.5", file.getContentDescription().getCharset().equals("ascii"));

		// getContentDescription will have noticed out-of-sync
		Job.getJobManager().wakeUp(ResourcesPlugin.FAMILY_AUTO_REFRESH);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, createTestMonitor());
		// Prime the cache...
		assertTrue("4.6", file.getCharset().equals("ascii"));

		// 3) Change the content type of the file under eclipse's feet
		try (FileWriter writer = new FileWriter(file.getLocation().toFile())) {
			writer.write(utf);
		}
		touchInFilesystem(file);
		// #getCharset uses the cached value (bug 209167) - doesn't check sync state
		assertTrue("5.4", file.getCharset().equals("ascii"));
		// #getContentDescription checks sync and discovers the real content type
		assertTrue("5.5", file.getContentDescription().getCharset().equals("UTF-8"));
		// getContentDescription will have noticed out-of-sync
		Job.getJobManager().wakeUp(ResourcesPlugin.FAMILY_AUTO_REFRESH);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, createTestMonitor());
		// #getCharset will now have noticed that the file has changed.
		assertTrue("5.6", file.getCharset().equals("UTF-8"));

		// 4) Change the content type of the file under eclipse's feet once more (to non-default).
		try (FileWriter writer = new FileWriter(file.getLocation().toFile())) {
			writer.write(ascii);
		}
		touchInFilesystem(file);
		// #getCharset uses the cached value (bug 209167) - doesn't check sync state
		assertTrue("6.7", file.getCharset().equals("UTF-8"));
		// #getContentDescription checks sync and discovers the real content type
		assertTrue("6.8", file.getContentDescription().getCharset().equals("ascii"));
		// getContentDescription will have noticed out-of-sync
		Job.getJobManager().wakeUp(ResourcesPlugin.FAMILY_AUTO_REFRESH);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, createTestMonitor());
		assertTrue("6.9", file.getCharset().equals("ascii"));
	}

	@Test
	public void testBug207510() throws CoreException, InterruptedException, BackingStoreException {
		IWorkspace workspace = getWorkspace();
		CharsetVerifier verifier = new CharsetVerifierWithExtraInfo(CharsetVerifier.IGNORE_BACKGROUND_THREAD);
		MultipleDeltasCharsetVerifier backgroundVerifier = new MultipleDeltasCharsetVerifier(CharsetVerifier.IGNORE_CREATION_THREAD);
		IProject project1 = workspace.getRoot().getProject("project1");
		try {
			workspace.addResourceChangeListener(verifier, IResourceChangeEvent.POST_CHANGE);
			workspace.addResourceChangeListener(backgroundVerifier, IResourceChangeEvent.POST_CHANGE);

			IFolder a1 = project1.getFolder("a1");
			IFolder b1 = project1.getFolder("b1");
			IFile a = a1.getFile("a.txt");
			createInWorkspace(new IResource[] {project1, a1, b1, a});
			verifier.reset();
			verifier.addExpectedChange(b1, IResourceDelta.CHANGED, IResourceDelta.DERIVED_CHANGED);
			b1.setDerived(true, createTestMonitor());
			verifier.waitForEvent(10000);
			IFile regularPrefs = getResourcesPreferenceFile(project1, false);
			IFile derivedPrefs = getResourcesPreferenceFile(project1, true);
			assertExistsInWorkspace(regularPrefs);
			assertDoesNotExistInWorkspace(derivedPrefs);

			//1 - setting preference on project
			verifier.reset();
			verifier.addExpectedChange(regularPrefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			setDerivedEncodingStoredSeparately(project1, true);
			assertTrue("1.1", verifier.waitForEvent(10000));
			assertTrue("1.2 " + verifier.getMessage(), verifier.isDeltaValid());
			assertExistsInWorkspace(regularPrefs);
			assertDoesNotExistInWorkspace(derivedPrefs);
			assertTrue("1.5", isDerivedEncodingStoredSeparately(project1));

			//2 - changing charset for file
			verifier.reset();
			verifier.addExpectedChange(a, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			a.setCharset("UTF-8", createTestMonitor());
			assertTrue("2.1", verifier.waitForEvent(10000));
			assertTrue("2.2 " + verifier.getMessage(), verifier.isDeltaValid());
			assertExistsInWorkspace(regularPrefs);
			assertDoesNotExistInWorkspace(derivedPrefs);

			//3 - setting derived == 'true' for file
			// TODO update the test when bug 345271 is fixed
			a.setDerived(true, createTestMonitor());
			//wait for all resource deltas
			// Thread.sleep(500);
			waitForCharsetManagerJob();
			assertExistsInWorkspace(regularPrefs);
			assertExistsInWorkspace(derivedPrefs);
			assertTrue("3.3", derivedPrefs.isDerived());

			//4 - setting derived == 'false' for file
			// TODO update the test when bug 345271 is fixed
			a.setDerived(false, createTestMonitor());
			//wait for all resource deltas
			// Thread.sleep(500);
			waitForCharsetManagerJob();
			assertExistsInWorkspace(regularPrefs);
			assertDoesNotExistInWorkspace(derivedPrefs);

			//5 - moving file to derived folder
			IFile source = project1.getFolder("a1").getFile("a.txt");
			IFile destination = project1.getFolder("b1").getFile("a.txt");
			backgroundVerifier.reset();
			backgroundVerifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.ADDED, 0);
			a.move(destination.getFullPath(), true, createTestMonitor());
			a = destination;
			waitForCharsetManagerJob();
			assertTrue("5.1", backgroundVerifier.waitForAllDeltas(10000, 15000));
			backgroundVerifier.assertExpectedDeltasWereReceived("5.2");
			assertExistsInWorkspace(regularPrefs);
			assertExistsInWorkspace(derivedPrefs);
			assertDoesNotExistInWorkspace(source);
			assertExistsInWorkspace(destination);
			assertTrue("5.7", derivedPrefs.isDerived());
			assertCharsetIs("5.8", "UTF-8", new IResource[] { a }, true);

			//6 - removing preference on project
			verifier.reset();
			backgroundVerifier.reset();
			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.REMOVED, 0);
			setDerivedEncodingStoredSeparately(project1, false);
			assertTrue("6.1.1", verifier.waitForEvent(10000));
			assertTrue("6.1.2", backgroundVerifier.waitForFirstDelta(10000));
			assertTrue("6.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			backgroundVerifier.assertExpectedDeltasWereReceived("6.2.2");
			assertExistsInWorkspace(regularPrefs);
			assertDoesNotExistInWorkspace(derivedPrefs);

			//7 - setting preference on project with derived files
			verifier.reset();
			backgroundVerifier.reset();
			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.ADDED, 0);
			setDerivedEncodingStoredSeparately(project1, true);
			assertTrue("7.1.1", verifier.waitForEvent(10000));
			assertTrue("7.1.2", backgroundVerifier.waitForFirstDelta(10000));
			assertTrue("7.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			backgroundVerifier.assertExpectedDeltasWereReceived("7.2.2");
			assertExistsInWorkspace(regularPrefs);
			assertExistsInWorkspace(derivedPrefs);
			assertTrue("7.5", isDerivedEncodingStoredSeparately(project1));
			assertTrue("7.6", derivedPrefs.isDerived());

			//			//8 - manually changing preference 'true'->'false'
			//			verifier.reset();
			//			backgroundVerifier.reset();
			//			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			//			backgroundVerifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(new IResource[] {project1, a1, b1, a, regularPrefs.getParent(), regularPrefs}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.REMOVED, 0);
			//			assertTrue("7.99", isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				regularPrefs.setContents(new ByteArrayInputStream(SAMPLE_DERIVED_ENCODING_TO_FALSE_REGULAR_PREFS.getBytes()), 0, getMonitor());
			//			} catch (CoreException e) {
			//				fail("8.0", e);
			//			}
			//			assertTrue("8.1.1", verifier.waitForEvent(10000));
			//			assertTrue("8.1.2", backgroundVerifier.waitForEvent(10000));
			//			assertTrue("8.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			//			assertTrue("8.2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			//			assertExistsInWorkspace("8.3", regularPrefs);
			//			//checkPreferencesContent("8.3.1", regularPrefs, SAMPLE_DERIVED_ENCODING_AFTER_FALSE_REGULAR_PREFS);
			//			assertDoesNotExistInWorkspace("8.4", derivedPrefs);
			//			assertTrue("8.5", !isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				assertCharsetIs("8.6", "UTF-8", new IResource[] {a}, true);
			//			} catch (CoreException e) {
			//				fail("8.6.1", e);
			//			}
			//
			//			//9 - manually changing preference 'false'->'true'
			//			verifier.reset();
			//			backgroundVerifier.reset();
			//			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			//			//backgroundVerifier.addExpectedChange(new IResource[] {project1, a1, b1, a, regularPrefs.getParent(), regularPrefs}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.ADDED, 0);
			//			assertDoesNotExistInWorkspace("8.98", derivedPrefs);
			//			assertTrue("8.99", !isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				regularPrefs.setContents(new ByteArrayInputStream(SAMPLE_DERIVED_ENCODING_TO_TRUE_REGULAR_PREFS.getBytes()), 0, getMonitor());
			//			} catch (CoreException e) {
			//				fail("9.0", e);
			//			}
			//			assertTrue("9.1.1", verifier.waitForEvent(10000));
			//			assertTrue("9.1.2", backgroundVerifier.waitForEvent(10000));
			//			assertTrue("9.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			//			assertTrue("9.2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			//			//updating prefs is run in separate job so wait some time for completion
			//			backgroundVerifier.waitForEvent(10000);
			//			assertExistsInWorkspace("9.3", regularPrefs);
			//			//checkPreferencesContent("9.3.1", regularPrefs, SAMPLE_DERIVED_ENCODING_AFTER_TRUE_REGULAR_PREFS);
			//			assertExistsInWorkspace("9.4", derivedPrefs);
			//			checkPreferencesContent("9.4.1", derivedPrefs, SAMPLE_DERIVED_ENCODING_AFTER_TRUE_DERIVED_PREFS);
			//			assertTrue("9.5", isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				assertCharsetIs("9.6", "UTF-8", new IResource[] {a}, true);
			//			} catch (CoreException e) {
			//				fail("9.6.1", e);
			//			}
			//			assertTrue("9.7", derivedPrefs.isDerived());
			//
			//			//10 - manually changing preference 'true'->'false' outside Eclipse
			//			verifier.reset();
			//			backgroundVerifier.reset();
			//			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			//			backgroundVerifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(new IResource[] {project1, a1, b1, a, regularPrefs.getParent(), regularPrefs}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.REMOVED, 0);
			//			assertTrue("9.99", isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				File file = regularPrefs.getLocation().toFile();
			//				BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			//				bw.write(SAMPLE_DERIVED_ENCODING_TO_FALSE_REGULAR_PREFS);
			//				bw.close();
			//				regularPrefs.refreshLocal(IResource.DEPTH_ZERO, getMonitor());
			//			} catch (IOException e) {
			//				fail("10.0.1", e);
			//			} catch (CoreException e) {
			//				fail("10.0.2", e);
			//			}
			//			assertTrue("10.1.1", verifier.waitForEvent(10000));
			//			assertTrue("10.1.2", backgroundVerifier.waitForEvent(10000));
			//			assertTrue("10.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			//			assertTrue("10.2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			//			assertExistsInWorkspace("10.3", regularPrefs);
			//			//checkPreferencesContent("10.3.1", regularPrefs, SAMPLE_DERIVED_ENCODING_AFTER_FALSE_REGULAR_PREFS);
			//			assertDoesNotExistInWorkspace("10.4", derivedPrefs);
			//			assertTrue("10.5", !isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				assertCharsetIs("10.6", "UTF-8", new IResource[] {a}, true);
			//			} catch (CoreException e) {
			//				fail("10.6.1", e);
			//			}
			//
			//			//11 - manually changing preference 'false'->'true' outside Eclipse
			//			verifier.reset();
			//			backgroundVerifier.reset();
			//			verifier.addExpectedChange(regularPrefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			//			//backgroundVerifier.addExpectedChange(new IResource[] {project1, a1, b1, a, regularPrefs.getParent(), regularPrefs}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			//			backgroundVerifier.addExpectedChange(derivedPrefs, IResourceDelta.ADDED, 0);
			//			assertDoesNotExistInWorkspace("10.98", derivedPrefs);
			//			assertTrue("10.99", !isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				File file = regularPrefs.getLocation().toFile();
			//				BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			//				bw.write(SAMPLE_DERIVED_ENCODING_TO_TRUE_REGULAR_PREFS);
			//				bw.close();
			//				regularPrefs.refreshLocal(IResource.DEPTH_ZERO, getMonitor());
			//			} catch (IOException e) {
			//				fail("11.0.1", e);
			//			} catch (CoreException e) {
			//				fail("11.0.2", e);
			//			}
			//			assertTrue("11.1.1", verifier.waitForEvent(10000));
			//			assertTrue("11.1.2", backgroundVerifier.waitForEvent(10000));
			//			assertTrue("11.2.1 " + verifier.getMessage(), verifier.isDeltaValid());
			//			assertTrue("11.2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			//			//updating prefs is run in separate job so wait some time for completion
			//			backgroundVerifier.waitForEvent(10000);
			//			assertExistsInWorkspace("11.3", regularPrefs);
			//			//checkPreferencesContent("11.3.1", regularPrefs, SAMPLE_DERIVED_ENCODING_AFTER_TRUE_REGULAR_PREFS);
			//			assertExistsInWorkspace("11.4", derivedPrefs);
			//			checkPreferencesContent("11.4.1", derivedPrefs, SAMPLE_DERIVED_ENCODING_AFTER_TRUE_DERIVED_PREFS);
			//			assertTrue("11.5", isDerivedEncodingStoredSeparately(project1));
			//			try {
			//				assertCharsetIs("11.6", "UTF-8", new IResource[] {a}, true);
			//			} catch (CoreException e) {
			//				fail("11.6.1", e);
			//			}
			//			assertTrue("11.7", derivedPrefs.isDerived());
		} finally {
			workspace.removeResourceChangeListener(verifier);
			backgroundVerifier.removeResourceChangeListeners();
			clearAllEncodings(project1);
		}
	}

	/**
	 * In this bug, a file starts with a particular content id and content type. It is then
	 * deleted and recreated, with the same content id but a different content type.
	 * This tricks the content type cache into returning an invalid result.
	 */
	@Test
	public void testBug261994() throws CoreException {
		//recreate a file with different contents but the same content id
		IWorkspace workspace = getWorkspace();
		IProject project1 = workspace.getRoot().getProject("Project1");
		IFile file = project1.getFile("file1.xml");
		createInWorkspace(file, SAMPLE_XML_ISO_8859_1_ENCODING);
		ContentDescriptionManagerTest.waitForCacheFlush();
		assertEquals("1.0", "ISO-8859-1", file.getCharset());

		//delete and recreate the file with different contents
		removeFromWorkspace(file);
		createInWorkspace(file, SAMPLE_XML_DEFAULT_ENCODING);
		assertEquals("2.0", "UTF-8", file.getCharset());
	}

	@Test
	public void testChangesDifferentProject() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project1 = workspace.getRoot().getProject("Project1");
		IProject project2 = workspace.getRoot().getProject("Project2");
		try {
			IFolder folder = project1.getFolder("folder1");
			IFile file1 = project1.getFile("file1.txt");
			IFile file2 = folder.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2, project2});
			project1.setDefaultCharset("FOO", createTestMonitor());
			project2.setDefaultCharset("ZOO", createTestMonitor());
			folder.setDefaultCharset("BAR", createTestMonitor());
			// move a folder to another project and ensure its encoding is
			// preserved
			folder.move(project2.getFullPath().append("folder"), false, false, null);
			folder = project2.getFolder("folder");
			assertEquals("1.0", "BAR", folder.getDefaultCharset());
			assertEquals("1.1", "BAR", folder.getFile("file2.txt").getCharset());
			// move a file with no charset set and check if it inherits
			// properly from the new parent
			assertEquals("2.0", project1.getDefaultCharset(), file1.getCharset());
			file1.move(project2.getFullPath().append("file1.txt"), false, false, null);
			file1 = project2.getFile("file1.txt");
			assertEquals("2.1", project2.getDefaultCharset(), file1.getCharset());
		} finally {
			clearAllEncodings(project1);
			clearAllEncodings(project2);
		}
	}

	@Test
	public void testChangesSameProject() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		try {
			IFolder folder = project.getFolder("folder1");
			IFile file1 = project.getFile("file1.txt");
			IFile file2 = folder.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2});
			project.setDefaultCharset("FOO", createTestMonitor());
			file1.setCharset("FRED", createTestMonitor());
			folder.setDefaultCharset("BAR", createTestMonitor());
			// move a folder inside the project and ensure its encoding is
			// preserved
			folder.move(project.getFullPath().append("folder2"), false, false, null);
			folder = project.getFolder("folder2");
			assertEquals("1.0", "BAR", folder.getDefaultCharset());
			assertEquals("1.1", "BAR", folder.getFile("file2.txt").getCharset());
			// move a file inside the project and ensure its encoding is
			// update accordingly
			file2 = folder.getFile("file2.txt");
			file2.move(project.getFullPath().append("file2.txt"), false, false, null);
			file2 = project.getFile("file2.txt");
			assertEquals("2.0", project.getDefaultCharset(), file2.getCharset());
			// delete a file and recreate it and ensure the encoding is not
			// remembered
			file1.delete(false, false, null);
			createInWorkspace(new IResource[] {file1});
			assertEquals("3.0", project.getDefaultCharset(), file1.getCharset());
		} finally {
			clearAllEncodings(project);
		}
	}

	@Test
	public void testClosingAndReopeningProject() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		try {
			// create a project and set some explicit encodings
			IFolder folder = project.getFolder("folder");
			IFile file1 = project.getFile("file1.txt");
			IFile file2 = folder.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2});
			project.setDefaultCharset("FOO", createTestMonitor());
			file1.setCharset("FRED", createTestMonitor());
			folder.setDefaultCharset("BAR", createTestMonitor());
			project.close(null);
			// now reopen the project and ensure the settings were not forgotten
			IProject projectB = workspace.getRoot().getProject(project.getName());
			projectB.open(null);
			assertExistsInWorkspace(getResourcesPreferenceFile(projectB, false));
			assertEquals("1.0", "FOO", projectB.getDefaultCharset());
			assertEquals("3.0", "FRED", projectB.getFile("file1.txt").getCharset());
			assertEquals("2.0", "BAR", projectB.getFolder("folder").getDefaultCharset());
			assertEquals("2.1", "BAR", projectB.getFolder("folder").getFile("file2.txt").getCharset());
		} finally {
			clearAllEncodings(project);
		}
	}

	/**
	 * Tests Content Manager-based charset setting.
	 */
	@Test
	public void testContentBasedCharset() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		try {
			createInWorkspace(project);
			project.setDefaultCharset("FOO", createTestMonitor());
			IFile file = project.getFile("file.xml");
			assertEquals("0.9", "FOO", project.getDefaultCharset());
			// content-based encoding is BAR
			createInWorkspace(file, SAMPLE_XML_US_ASCII_ENCODING);
			assertEquals("1.0", "US-ASCII", file.getCharset());
			// content-based encoding is FRED
			file.setContents(new ByteArrayInputStream(SAMPLE_XML_ISO_8859_1_ENCODING.getBytes(StandardCharsets.ISO_8859_1)), false, false, null);
			assertEquals("2.0", "ISO-8859-1", file.getCharset());
			// content-based encoding is UTF-8 (default for XML)
			file.setContents(new ByteArrayInputStream(SAMPLE_XML_DEFAULT_ENCODING.getBytes(StandardCharsets.UTF_8)), false, false, null);
			assertEquals("3.0", "UTF-8", file.getCharset());
			// tests with BOM -BOMs are strings for convenience, encoded itno bytes using ISO-8859-1 (which handles 128-255 bytes better)
			// tests with UTF-8 BOM
			String UTF8_BOM = new String(IContentDescription.BOM_UTF_8, StandardCharsets.ISO_8859_1);
			file.setContents(new ByteArrayInputStream((UTF8_BOM + SAMPLE_XML_DEFAULT_ENCODING).getBytes(StandardCharsets.ISO_8859_1)), false, false, null);
			assertEquals("4.0", "UTF-8", file.getCharset());
			// tests with UTF-16 Little Endian BOM
			String UTF16_LE_BOM = new String(IContentDescription.BOM_UTF_16LE, StandardCharsets.ISO_8859_1);
			file.setContents(new ByteArrayInputStream((UTF16_LE_BOM + SAMPLE_XML_DEFAULT_ENCODING).getBytes(StandardCharsets.ISO_8859_1)), false, false, null);
			assertEquals("5.0", "UTF-16", file.getCharset());
			// tests with UTF-16 Big Endian BOM
			String UTF16_BE_BOM = new String(IContentDescription.BOM_UTF_16BE, StandardCharsets.ISO_8859_1);
			file.setContents(new ByteArrayInputStream((UTF16_BE_BOM + SAMPLE_XML_DEFAULT_ENCODING).getBytes(StandardCharsets.ISO_8859_1)), false, false, null);
			assertEquals("6.0", "UTF-16", file.getCharset());
		} finally {
			clearAllEncodings(project);
		}
	}

	@Test
	public void testDefaults() throws CoreException {
		IProject project = null;
		try {
			IWorkspace workspace = getWorkspace();
			project = workspace.getRoot().getProject("MyProject");
			IFolder folder1 = project.getFolder("folder1");
			IFolder folder2 = folder1.getFolder("folder2");
			IFile file1 = project.getFile("file1.txt");
			IFile file2 = folder1.getFile("file2.txt");
			IFile file3 = folder2.getFile("file3.txt");

			createInWorkspace(new IResource[] { project });
			assertEquals(ResourcesPlugin.getEncoding(), project.getDefaultCharset(false));

			IMarker[] markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("No missing encoding marker should be set", 0, markers.length);

			project.setDefaultCharset(null, createTestMonitor());
			assertEquals(null, project.getDefaultCharset(false));

			waitForEncodingRelatedJobs(testName.getMethodName());

			markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("Missing encoding marker should be set", 1, markers.length);

			createInWorkspace(new IResource[] {file1, file2, file3});
			// project and children should be using the workspace's default now
			assertCharsetIs("1.0", ResourcesPlugin.getEncoding(), new IResource[] {workspace.getRoot(), project, file1, folder1, file2, folder2, file3}, true);
			assertCharsetIs("1.1", null, new IResource[] {project, file1, folder1, file2, folder2, file3}, false);

			// sets workspace default charset
			workspace.getRoot().setDefaultCharset("FOO", createTestMonitor());
			markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("Missing encoding marker should be still set", 1, markers.length);

			assertCharsetIs("2.0", "FOO", new IResource[] {workspace.getRoot(), project, file1, folder1, file2, folder2, file3}, true);
			assertCharsetIs("2.1", null, new IResource[] {project, file1, folder1, file2, folder2, file3}, false);

			// sets project default charset
			project.setDefaultCharset("BAR", createTestMonitor());
			waitForEncodingRelatedJobs(testName.getMethodName());

			markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("No missing encoding marker should be set", 0, markers.length);

			assertCharsetIs("3.0", "BAR", new IResource[] {project, file1, folder1, file2, folder2, file3}, true);
			assertCharsetIs("3.1", null, new IResource[] {file1, folder1, file2, folder2, file3}, false);
			assertCharsetIs("3.2", "FOO", new IResource[] {workspace.getRoot()}, true);
			// sets folder1 default charset
			folder1.setDefaultCharset("FRED", createTestMonitor());
			assertCharsetIs("4.0", "FRED", new IResource[] {folder1, file2, folder2, file3}, true);
			assertCharsetIs("4.1", null, new IResource[] {file2, folder2, file3}, false);
			assertCharsetIs("4.2", "BAR", new IResource[] {project, file1}, true);
			// sets folder2 default charset
			folder2.setDefaultCharset("ZOO", createTestMonitor());
			assertCharsetIs("5.0", "ZOO", new IResource[] {folder2, file3}, true);
			assertCharsetIs("5.1", null, new IResource[] {file3}, false);
			assertCharsetIs("5.2", "FRED", new IResource[] {folder1, file2}, true);
			// sets file3 charset
			file3.setCharset("ZIT", createTestMonitor());
			assertCharsetIs("6.0", "ZIT", new IResource[] {file3}, false);
			folder2.setDefaultCharset(null, createTestMonitor());
			assertCharsetIs("7.0", folder2.getParent().getDefaultCharset(), new IResource[] {folder2}, true);
			assertCharsetIs("7.1", null, new IResource[] {folder2}, false);
			assertCharsetIs("7.2", "ZIT", new IResource[] {file3}, false);
			folder1.setDefaultCharset(null, createTestMonitor());
			assertCharsetIs("8.0", folder1.getParent().getDefaultCharset(), new IResource[] {folder1, file2, folder2}, true);
			assertCharsetIs("8.1", null, new IResource[] {folder1, file2, folder2}, false);
			assertCharsetIs("8.2", "ZIT", new IResource[] {file3}, false);
			project.setDefaultCharset(null, createTestMonitor());
			assertCharsetIs("9.0", project.getParent().getDefaultCharset(), new IResource[] {project, file1, folder1, file2, folder2}, true);
			assertCharsetIs("9.1", null, new IResource[] {project, file1, folder1, file2, folder2}, false);
			assertCharsetIs("9.2", "ZIT", new IResource[] {file3}, false);
			workspace.getRoot().setDefaultCharset(null, createTestMonitor());
			assertCharsetIs("10.0", project.getParent().getDefaultCharset(), new IResource[] {project, file1, folder1, file2, folder2}, true);
			assertCharsetIs("10.1", "ZIT", new IResource[] {file3}, false);
			file3.setCharset(null, createTestMonitor());
			assertCharsetIs("11.0", ResourcesPlugin.getEncoding(), new IResource[] {workspace.getRoot(), project, file1, folder1, file2, folder2, file3}, true);
		} finally {
			clearAllEncodings(project);
		}
	}

	// check we react to content type changes
	@Test
	public void testDeltaOnContentTypeChanges() throws CoreException {
		final String USER_SETTING = "USER_CHARSET";
		final String PROVIDER_SETTING = "PROVIDER_CHARSET";

		// install a verifier
		CharsetVerifier backgroundVerifier = new CharsetVerifier(CharsetVerifier.IGNORE_CREATION_THREAD);
		getWorkspace().addResourceChangeListener(backgroundVerifier, IResourceChangeEvent.POST_CHANGE);
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		IContentType myType = contentTypeManager.getContentType("org.eclipse.core.tests.resources.myContent2");
		assertNotNull("0.1", myType);
		assertEquals("0.2", PROVIDER_SETTING, myType.getDefaultCharset());
		IProject project = getWorkspace().getRoot().getProject("project1");
		try {
			IFolder folder1 = project.getFolder("folder1");
			IFile file1 = folder1.getFile("file1.txt");
			IFile file2 = folder1.getFile("file2.resources-mc");
			IFile file3 = project.getFile("file3.resources-mc");
			IFile file4 = project.getFile("file4.resources-mc");
			createInWorkspace(new IResource[] {file1, file2, file3, file4});
			project.setDefaultCharset("FOO", createTestMonitor());
			// even files with a user-set charset will appear in the delta
			file4.setCharset("BAR", createTestMonitor());
			// configure verifier
			backgroundVerifier.reset();
			backgroundVerifier.addExpectedChange(new IResource[] {file2, file3, file4}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			// change content type's default charset
			myType.setDefaultCharset(USER_SETTING);
			// ensure the property events were generated
			assertTrue("2.1", backgroundVerifier.waitForEvent(10000));
			assertTrue("2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			assertEquals("3.0", USER_SETTING, file2.getCharset());
			assertEquals("3.1", USER_SETTING, file3.getCharset());
			assertEquals("3.2", "BAR", file4.getCharset());

			// change back to the provider-provided default
			// configure verifier
			backgroundVerifier.reset();
			backgroundVerifier.addExpectedChange(new IResource[] {file2, file3, file4}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			// reset charset to default
			myType.setDefaultCharset(null);
			// ensure the property events were generated
			assertTrue("4.1", backgroundVerifier.waitForEvent(10000));
			assertTrue("4.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());
			assertEquals("5.0", PROVIDER_SETTING, file2.getCharset());
			assertEquals("5.1", PROVIDER_SETTING, file3.getCharset());
			assertEquals("5.2", "BAR", file4.getCharset());
		} finally {
			getWorkspace().removeResourceChangeListener(backgroundVerifier);
			myType.setDefaultCharset(null);
			clearAllEncodings(project);
		}
	}

	// check preference change events are reflected in the charset settings
	// temporarily disabled+
	@Test
	public void testDeltaOnPreferenceChanges() throws IOException, CoreException {
		CharsetVerifier backgroundVerifier = new CharsetVerifier(CharsetVerifier.IGNORE_CREATION_THREAD);
		getWorkspace().addResourceChangeListener(backgroundVerifier, IResourceChangeEvent.POST_CHANGE);
		IProject project = getWorkspace().getRoot().getProject("project1");
		try {
			IFolder folder1 = project.getFolder("folder1");
			IFile file1 = folder1.getFile("file1.txt");
			IFile file2 = project.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2});

			IFile resourcesPrefs = getResourcesPreferenceFile(project, false);
			assertTrue("0.9", resourcesPrefs.exists());
			String prefsContent = Files.readString(resourcesPrefs.getLocation().toFile().toPath());
			assertTrue(prefsContent.contains(ResourcesPlugin.getEncoding()));
			file1.setCharset("CHARSET1", createTestMonitor());
			assertTrue("1.1", resourcesPrefs.exists());
			waitForCharsetManagerJob();

			prefsContent = Files.readString(resourcesPrefs.getLocation().toFile().toPath());
			assertTrue(prefsContent.contains(ResourcesPlugin.getEncoding()));

			backgroundVerifier.reset();
			backgroundVerifier.addExpectedChange(new IResource[] {project, folder1, file1, file2, resourcesPrefs, resourcesPrefs.getParent()}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			// cause a resource change event without actually changing contents
			InputStream contents = new ByteArrayInputStream(prefsContent.getBytes());
			resourcesPrefs.setContents(contents, 0, createTestMonitor());
			assertTrue("2.1", backgroundVerifier.waitForEvent(10000));
			assertTrue("2.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());

			IMarker[] markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("No missing encoding marker should be set", 0, markers.length);

			backgroundVerifier.reset();
			backgroundVerifier.addExpectedChange(
					new IResource[] { project }, IResourceDelta.CHANGED,
					IResourceDelta.ENCODING | IResourceDelta.MARKERS);
			backgroundVerifier.addExpectedChange(new IResource[] { folder1, file1, file2, resourcesPrefs.getParent() },
					IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			// delete the preferences file
			resourcesPrefs.delete(true, createTestMonitor());
			waitForCharsetManagerJob();
			waitForEncodingRelatedJobs(testName.getMethodName());

			assertTrue("3.1", backgroundVerifier.waitForEvent(10000));
			assertTrue("3.2 " + backgroundVerifier.getMessage(), backgroundVerifier.isDeltaValid());

			markers = project.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ONE);
			assertEquals("Missing encoding marker should be set", 1, markers.length);
		} finally {
			getWorkspace().removeResourceChangeListener(backgroundVerifier);
			clearAllEncodings(project);
		}
	}

	/**
	 * Test the contents of the resource deltas which are generated
	 * when we make encoding changes to containers (folders, projects, root).
	 */
	@Test
	public void testDeltasContainer() throws CoreException {
		MultipleDeltasCharsetVerifier verifier = new MultipleDeltasCharsetVerifier(CharsetVerifier.IGNORE_BACKGROUND_THREAD);
		IProject project = getWorkspace().getRoot().getProject(createUniqueString());
		getWorkspace().addResourceChangeListener(verifier, IResourceChangeEvent.POST_CHANGE);
		try {
			IFile prefs = getResourcesPreferenceFile(project, false);
			// leaf folder
			IFolder folder1 = project.getFolder("folder1");
			createInWorkspace(new IResource[] {project, folder1});
			verifier.reset();
			verifier.addExpectedChange(folder1, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(new IResource[] { prefs.getParent() }, IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(new IResource[] { prefs }, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset("new_charset", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("1.1.");

			// folder with children
			IFolder folder2 = folder1.getFolder("folder2");
			IFile file1 = folder1.getFile("file1.txt");
			IFile file2 = folder2.getFile("file2.txt");
			createInWorkspace(new IResource[] {folder2, file1, file2});
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {folder1, folder2, file1, file2}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset("a_charset", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("2.1.");

			// folder w. children, some with non-inherited values
			// set the child to have a non-inherited value
			folder2.setDefaultCharset("non-Default", createTestMonitor());
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {folder1, file1}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset("newOne", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("3.2.");

			// change from non-default to another non-default
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {folder1, file1}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset("newTwo", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("4.2.");

			// change to default (clear it)
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {folder1, file1}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset(null, createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("5.1.");

			// change to default (equal to it but it doesn't inherit)
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {folder1, file1}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			folder1.setDefaultCharset(project.getDefaultCharset(), createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("6.1.");

			// clear all the encoding info before we start working with the project
			clearAllEncodings(project);
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {project, folder1, folder2, file1, file2, prefs.getParent()}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs, IResourceDelta.ADDED, 0);
			project.setDefaultCharset("foo", createTestMonitor());
			waitForEncodingRelatedJobs(testName.getMethodName());
			verifier.assertExpectedDeltasWereReceived("7.2.");

			// clear all the encoding info before we start working with the root
			clearAllEncodings(project);
			verifier.reset();
			verifier.addExpectedChange(new IResource[] {project, folder1, folder2, file1, file2, prefs.getParent()}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			getWorkspace().getRoot().setDefaultCharset("foo", createTestMonitor());
			waitForEncodingRelatedJobs(testName.getMethodName());
			verifier.assertExpectedDeltasWereReceived("8.2.");
		} finally {
			verifier.removeResourceChangeListeners();
			clearAllEncodings(project);
		}
	}

	/**
	 * Check that we are broadcasting the correct resource deltas when
	 * making encoding changes.
	 */
	@Test
	public void testDeltasFile() throws CoreException {
		IWorkspace workspace = getWorkspace();
		MultipleDeltasCharsetVerifier verifier = new MultipleDeltasCharsetVerifier(
				CharsetVerifier.IGNORE_BACKGROUND_THREAD);
		workspace.addResourceChangeListener(verifier, IResourceChangeEvent.POST_CHANGE);
		final IProject project = workspace.getRoot().getProject("MyProject");
		try {
			IFile prefs = getResourcesPreferenceFile(project, false);
			// File:
			// single file
			final IFile file1 = project.getFile("file1.txt");
			createInWorkspace(file1, createRandomString());
			// change from default
			verifier.reset();
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(new IResource[] { prefs.getParent() }, IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(new IResource[] { prefs }, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			file1.setCharset("FOO", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("1.0.1");

			// change to default (clear it)
			verifier.reset();
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			file1.setCharset(null, createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("1.1.1");

			// change to default (equal to it but it doesn't inherit)
			verifier.reset();
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			file1.setCharset(project.getDefaultCharset(), createTestMonitor());

			verifier.assertExpectedDeltasWereReceived("1.2.1");

			// change from non-default to another non-default
			// sets to a non-default value first
			file1.setCharset("FOO", createTestMonitor());

			verifier.reset();
			verifier.addExpectedChange(file1, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			// sets to another non-defauilt value
			file1.setCharset("BAR", createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("1.3.2");

			// multiple files (same operation)
			verifier.reset();
			final IFile file2 = project.getFile("file2.txt");
			createInWorkspace(file2, createRandomString());
			verifier.addExpectedChange(new IResource[] {file1, file2}, IResourceDelta.CHANGED, IResourceDelta.ENCODING);
			verifier.addExpectedChange(prefs.getParent(), IResourceDelta.CHANGED, 0);
			verifier.addExpectedChange(prefs, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
			workspace.run((IWorkspaceRunnable) monitor -> {
				file1.setCharset("FOO", createTestMonitor());
				file2.setCharset("FOO", createTestMonitor());
			}, createTestMonitor());
			verifier.assertExpectedDeltasWereReceived("1.4.1");
		} finally {
			verifier.removeResourceChangeListeners();
			clearAllEncodings(project);
		}
	}

	@Test
	public void testFileCreation() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		try {
			IFolder folder = project.getFolder("folder");
			IFile file1 = project.getFile("file1.txt");
			IFile file2 = folder.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2});
			assertExistsInWorkspace(getResourcesPreferenceFile(project, false));
			project.setDefaultCharset("FOO", createTestMonitor());
			assertExistsInWorkspace(getResourcesPreferenceFile(project, false));
			project.setDefaultCharset(null, createTestMonitor());
			assertDoesNotExistInWorkspace(getResourcesPreferenceFile(project, false));
			file1.setCharset("FRED", createTestMonitor());
			assertExistsInWorkspace(getResourcesPreferenceFile(project, false));
			folder.setDefaultCharset("BAR", createTestMonitor());
			assertExistsInWorkspace(getResourcesPreferenceFile(project, false));
			file1.setCharset(null, createTestMonitor());
			assertExistsInWorkspace(getResourcesPreferenceFile(project, false));
			folder.setDefaultCharset(null, createTestMonitor());
			assertDoesNotExistInWorkspace(getResourcesPreferenceFile(project, false));
		} finally {
			clearAllEncodings(project);
		}
	}

	/**
	 * See enhancement request 60636.
	 */
	@Test
	public void testGetCharsetFor() throws CoreException {
		IProject project = null;
		try {
			IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
			//			IContentType text = contentTypeManager.getContentType("org.eclipse.core.runtime.text");
			IContentType xml = contentTypeManager.getContentType("org.eclipse.core.runtime.xml");

			IWorkspace workspace = getWorkspace();
			project = workspace.getRoot().getProject("MyProject");
			IFile oldFile = project.getFile("oldfile.xml");
			IFile newXMLFile = project.getFile("newfile.xml");
			IFile newTXTFile = project.getFile("newfile.txt");
			IFile newRandomFile = project.getFile("newFile." + (long) (Math.random() * (Long.MAX_VALUE)));
			createInWorkspace(oldFile, SAMPLE_XML_DEFAULT_ENCODING);
			// sets project default charset
			project.setDefaultCharset("BAR", createTestMonitor());
			oldFile.setCharset("FOO", createTestMonitor());
			// project and non-existing file share the same encoding
			assertCharsetIs("0.1", "BAR", new IResource[] {project, newXMLFile, newTXTFile, newRandomFile}, true);
			// existing file has encoding determined by user
			assertCharsetIs("0.2", "FOO", new IResource[] {oldFile}, true);

			// for an existing file, user set charset will prevail (if any)
			assertEquals("1.0", "FOO", oldFile.getCharsetFor(getTextContents("")));
			assertEquals("1.1", "FOO", oldFile.getCharsetFor(getTextContents(SAMPLE_XML_DEFAULT_ENCODING)));

			// for a non-existing file, or for a file that exists but does not have a user set charset,  the content type (if any) rules
			assertEquals("2.0", xml.getDefaultCharset(), newXMLFile.getCharsetFor(getTextContents("")));
			assertEquals("2.1", xml.getDefaultCharset(), newXMLFile.getCharsetFor(getTextContents(SAMPLE_XML_DEFAULT_ENCODING)));
			assertEquals("2.2", "US-ASCII", newXMLFile.getCharsetFor(getTextContents(SAMPLE_XML_US_ASCII_ENCODING)));
			oldFile.setCharset(null, createTestMonitor());
			assertEquals("2.3", xml.getDefaultCharset(), oldFile.getCharsetFor(getTextContents("")));
			assertEquals("2.4", xml.getDefaultCharset(), oldFile.getCharsetFor(getTextContents(SAMPLE_XML_DEFAULT_ENCODING)));
			assertEquals("2.5", "US-ASCII", oldFile.getCharsetFor(getTextContents(SAMPLE_XML_US_ASCII_ENCODING)));

			// if the content type has no default charset, or the file has no known content type, fallback to parent default charset
			assertEquals("3.0", project.getDefaultCharset(), newTXTFile.getCharsetFor(getTextContents("")));
			assertEquals("3.1", project.getDefaultCharset(), newRandomFile.getCharsetFor(getTextContents("")));
		} finally {
			clearAllEncodings(project);
		}

	}

	/**
	 * Moves a project and ensures the charsets are preserved.
	 */
	@Test
	public void testMovingProject() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project1 = workspace.getRoot().getProject("Project1");
		IProject project2 = null;
		try {
			IFolder folder = project1.getFolder("folder1");
			IFile file1 = project1.getFile("file1.txt");
			IFile file2 = folder.getFile("file2.txt");
			createInWorkspace(new IResource[] {file1, file2});
			project1.setDefaultCharset("FOO", createTestMonitor());
			folder.setDefaultCharset("BAR", createTestMonitor());

			assertEquals("1.0", "BAR", folder.getDefaultCharset());
			assertEquals("1.1", "BAR", file2.getCharset());
			assertEquals("1.2", "FOO", file1.getCharset());
			assertEquals("1.3", "FOO", project1.getDefaultCharset());

			// move project and ensures charsets settings are preserved
			project1.move(IPath.fromOSString("Project2"), false, null);
			project2 = workspace.getRoot().getProject("Project2");
			folder = project2.getFolder("folder1");
			file1 = project2.getFile("file1.txt");
			file2 = folder.getFile("file2.txt");
			assertEquals("2.0", "BAR", folder.getDefaultCharset());
			assertEquals("2.1", "BAR", file2.getCharset());
			assertEquals("2.2", "FOO", project2.getDefaultCharset());
			assertEquals("2.3", "FOO", file1.getCharset());
		} finally {
			clearAllEncodings(project1);
			clearAllEncodings(project2);
		}
	}

	/**
	 * Two things to test here:
	 * 	- non-existing resources default to the parent's default charset;
	 * 	- cannot set the charset for a non-existing resource (exception is thrown).
	 */
	@Test
	public void testNonExistingResource() throws CoreException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject("MyProject");
		try {
			CoreException e = assertThrows(CoreException.class, () -> project.setDefaultCharset("FOO", createTestMonitor()));
			assertEquals("project should not exist yet", IResourceStatus.RESOURCE_NOT_FOUND, e.getStatus().getCode());
			createInWorkspace(project);
			project.setDefaultCharset("FOO", createTestMonitor());
			IFile file = project.getFile("file.xml");
			assertDoesNotExistInWorkspace(file);
			assertEquals("2.2", "FOO", file.getCharset());
			e = assertThrows(CoreException.class, () -> file.setCharset("BAR", createTestMonitor()));
			assertEquals("file should not exist yet", IResourceStatus.RESOURCE_NOT_FOUND, e.getStatus().getCode());
			createInWorkspace(file);
			file.setCharset("BAR", createTestMonitor());
			assertEquals("2.8", "BAR", file.getCharset());
			file.delete(IResource.NONE, null);
			assertDoesNotExistInWorkspace(file);
			assertEquals("2.11", "FOO", file.getCharset());
		} finally {
			clearAllEncodings(project);
		}
	}

	@Test
	public void testBug464072() throws CoreException {
		getResourcesPreferences().putBoolean(ResourcesPlugin.PREF_LIGHTWEIGHT_AUTO_REFRESH, true);
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(createUniqueString());
		IFile file = project.getFile("file.txt");
		createInWorkspace(file);
		file.getLocation().toFile().delete();
		CoreException e = assertThrows(CoreException.class, file::getContentDescription);
		assertEquals("the resource should not exist", IResourceStatus.RESOURCE_NOT_FOUND, e.getStatus().getCode());
	}

	@Test
	public void testBug528827() throws CoreException, OperationCanceledException, InterruptedException {
		IWorkspace workspace = getWorkspace();
		IProject project = workspace.getRoot().getProject(createUniqueString());
		createInWorkspace(project);
		JobChangeAdapterExtension listener = new JobChangeAdapterExtension();
		Job.getJobManager().addJobChangeListener(listener);
		try {
			String otherCharset = getOtherCharset(workspace.getRoot().getDefaultCharset());
			project.setDefaultCharset(otherCharset, createTestMonitor());
			assertEquals(otherCharset, project.getDefaultCharset());
			project.delete(false, createTestMonitor());
			Thread.sleep(100); // leave some time for CharsetDeltaJob.to be scheduled;
			Job.getJobManager().wakeUp(CharsetDeltaJob.FAMILY_CHARSET_DELTA);
			Job.getJobManager().join(CharsetDeltaJob.FAMILY_CHARSET_DELTA, createTestMonitor());
			assertTrue(listener.getResult().isOK());
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}
	}

	private String getOtherCharset(String referenceCharset) {
		return "MIK".equals(referenceCharset) ? "UTF-8" : "MIK";
	}

	static void waitForCharsetManagerJob() {
		waitForJobFamily(CharsetManager.class);
	}

	static void waitForNotificationManagerJob() {
		waitForJobFamily(NotificationManager.class);
	}

	private static void waitForJobFamily(Object family) {
		TestUtil.waitForJobs("Waiting for " + family, 100, 10_000, family);
	}

	private class CharsetVerifierWithExtraInfo extends CharsetVerifier {
		CharsetVerifierWithExtraInfo(int flags) {
			super(flags);
		}

		@Override
		public void verifyDelta(IResourceDelta delta) {
			appendToMessage("Delta verification triggered:");
			appendToMessage(Arrays.toString(Thread.currentThread().getStackTrace()));
			LinkedList<IResourceDelta> queue = new LinkedList<>();
			queue.add(delta);
			while (!queue.isEmpty()) {
				IResourceDelta current = queue.removeFirst();
				if (current != null) {
					appendToMessage(
							"delta child: " + current + ", with flags: " + convertChangeFlags(current.getFlags()));
					IResourceDelta[] children = current.getAffectedChildren();
					if (children != null) {
						queue.addAll(Arrays.asList(children));
					}
				}
			}
			super.verifyDelta(delta);
		}
	}

	/**
	 * Verifies expected changes either as a single received delta, or as multiple
	 * deltas each containing exactly 1 expected change. If multiple deltas are
	 * received, those are verified in order of the added expected changes.
	 *
	 * This verifier succeeds if either all expected changes are received in a
	 * single delta, or if each expected change is received in a delta of its own.
	 */
	private class MultipleDeltasCharsetVerifier extends org.junit.Assert implements IResourceChangeListener {

		private final int flags;
		private final CharsetVerifier singleDeltaVerifier;
		private final List<CharsetVerifier> deltaVerifiers;
		private int expectedDeltasCount;
		private int verifiedDeltasCount;

		MultipleDeltasCharsetVerifier(int flags) {
			this.flags = flags;
			singleDeltaVerifier = new CharsetVerifierWithExtraInfo(flags);
			deltaVerifiers = new ArrayList<>();
			expectedDeltasCount = 0;
			verifiedDeltasCount = 0;
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			if (!singleDeltaVerifier.ignoreEvent()) {
				singleDeltaVerifier.resourceChanged(event);
				if (verifiedDeltasCount < deltaVerifiers.size()) {
					CharsetVerifier deltaVerifier = deltaVerifiers.get(verifiedDeltasCount);
					deltaVerifier.resourceChanged(event);
					verifiedDeltasCount++;
				}
			}
		}

		/**
		 * @param firstDeltaTimeout
		 *            the maximal time to wait in milliseconds until the first delta is
		 *            received
		 * @param lastDeltaTimeout
		 *            the maximal time to wait in milliseconds until the last delta is
		 *            received
		 * @return true if waiting was successful, false otherwise (i.e. false if
		 *         timeout occurred)
		 */
		boolean waitForAllDeltas(long firstDeltaTimeout, long lastDeltaTimeout) {
			boolean firstDeltaReceived = waitForFirstDelta(firstDeltaTimeout);
			long waitingStart = System.currentTimeMillis();
			if (firstDeltaReceived) {
				boolean isSingleDelta = singleDeltaVerifier.isDeltaValid();
				if (isSingleDelta) {
					return true;
				}
			}
			if (expectedDeltasCount > 0) {
				CharsetVerifier deltaVerifier = deltaVerifiers.get(deltaVerifiers.size() - 1);
				long waitedSoFar = System.currentTimeMillis() - waitingStart;
				long waitTimeout = lastDeltaTimeout - waitedSoFar;
				return deltaVerifier.waitForEvent(waitTimeout);
			}
			return true;
		}

		boolean waitForFirstDelta(long timeout) {
			return singleDeltaVerifier.waitForEvent(timeout);
		}

		void reset() {
			singleDeltaVerifier.reset();
			for (CharsetVerifier deltaVerifier : deltaVerifiers) {
				deltaVerifier.reset();
			}
			deltaVerifiers.clear();
		}

		void assertExpectedDeltasWereReceived(String message) {
			waitForNotificationManagerJob();
			if (expectedDeltasCount > 0 && verifiedDeltasCount == 0) {
				fail(message + ", expected " + expectedDeltasCount + " deltas but received no deltas");
			}
			if (verifiedDeltasCount > 0 && expectedDeltasCount == 0) {
				fail(message + ", expected no deltas but received " + verifiedDeltasCount + " deltas");
			}
			if (!singleDeltaVerifier.isDeltaValid()) {
				if (verifiedDeltasCount == 1) {
					fail(message + ", " + singleDeltaVerifier.getMessage());
				} else {
					boolean validDeltas = true;
					StringBuilder failMessage = new StringBuilder(message);
					for (int i = 0; i < expectedDeltasCount; i++) {
						CharsetVerifier deltaVerifier = deltaVerifiers.get(i);
						boolean isValidDelta = deltaVerifier.isDeltaValid();
						validDeltas &= isValidDelta;
						failMessage.append(System.lineSeparator());
						failMessage.append("listing verification result for expected change with index " + i);
						failMessage.append(System.lineSeparator());
						failMessage.append("verifier.isValidDelta(): " + isValidDelta);
						failMessage.append(System.lineSeparator());
						failMessage.append(deltaVerifier.getMessage());
					}
					assertTrue(failMessage.toString(), validDeltas);
				}
			}
		}

		void addExpectedChange(IResource resource, int status, int changeFlags) {
			addExpectedChange(new IResource[] { resource }, status, changeFlags);
		}

		void addExpectedChange(IResource[] resources, int status, int changeFlags) {
			singleDeltaVerifier.addExpectedChange(resources, status, changeFlags);
			CharsetVerifier deltaVerifier = new CharsetVerifierWithExtraInfo(flags);
			deltaVerifier.addExpectedChange(resources, status, changeFlags);
			deltaVerifiers.add(deltaVerifier);
			expectedDeltasCount++;
		}

		void removeResourceChangeListeners() {
			getWorkspace().removeResourceChangeListener(this);
		}
	}

}
