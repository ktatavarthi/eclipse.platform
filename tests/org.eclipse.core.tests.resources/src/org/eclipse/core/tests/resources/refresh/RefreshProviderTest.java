/*******************************************************************************
 *  Copyright (c) 2004, 2015 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.resources.refresh;

import junit.framework.AssertionFailedError;
import junit.framework.TestSuite;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.tests.resources.ResourceTest;

/**
 * Tests the IRefreshMonitor interface
 */
public class RefreshProviderTest extends ResourceTest {

	public static TestSuite suite() {
		return new TestSuite(RefreshProviderTest.class);
	}

	public RefreshProviderTest() {
		super();
	}

	public RefreshProviderTest(String name) {
		super(name);
	}

	/*(non-javadoc)
	 * Method declared on TestCase.
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		TestRefreshProvider.reset();
		//turn on autorefresh
		Preferences preferences = ResourcesPlugin.getPlugin().getPluginPreferences();
		preferences.setValue(ResourcesPlugin.PREF_AUTO_REFRESH, true);

	}

	/*(non-javadoc)
	 * Method declared on TestCase.
	 */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		//turn off autorefresh
		TestRefreshProvider.reset();
		Preferences preferences = ResourcesPlugin.getPlugin().getPluginPreferences();
		preferences.setValue(ResourcesPlugin.PREF_AUTO_REFRESH, false);
	}

	/**
	 * Test to ensure that a refresh provider is given the correct events when a linked
	 * file is created and deleted.
	 */
	public void testLinkedFile() throws InterruptedException {
		IPath location = getRandomLocation();
		try {
			IProject project = getWorkspace().getRoot().getProject("testUnmonitorLinkedResource");
			ensureExistsInWorkspace(project, true);
			joinAutoRefreshJobs();
			IFile link = project.getFile("Link");
			//ensure we currently have just the project being monitored
			TestRefreshProvider provider = TestRefreshProvider.getInstance();
			assertEquals("1.0", 1, provider.getMonitoredResources().length);
			link.createLink(location, IResource.ALLOW_MISSING_LOCAL, getMonitor());
			joinAutoRefreshJobs();
			assertEquals("1.1", 2, provider.getMonitoredResources().length);
			link.delete(IResource.FORCE, getMonitor());
			joinAutoRefreshJobs();
			assertEquals("1.2", 1, provider.getMonitoredResources().length);
			ensureDoesNotExistInWorkspace(project);
			joinAutoRefreshJobs();
			assertEquals("1.3", 0, provider.getMonitoredResources().length);
			//check provider for other errors
			AssertionFailedError[] failures = provider.getFailures();
			if (failures.length > 0)
				fail("" + failures.length + " failures", failures[0]);
		} catch (CoreException e) {
			fail("1.99", e);
		} finally {
			//cleanup
			Workspace.clear(location.toFile());
		}
	}

	/**
	 * Test to ensure that a refresh provider is given the correct events when a project
	 * is closed or opened.
	 */
	public void testProjectCloseOpen() throws InterruptedException {
		try {
			IProject project = getWorkspace().getRoot().getProject("testProjectCloseOpen");
			ensureExistsInWorkspace(project, true);
			joinAutoRefreshJobs();
			//ensure we currently have just the project being monitored
			TestRefreshProvider provider = TestRefreshProvider.getInstance();
			assertEquals("1.0", 1, provider.getMonitoredResources().length);
			project.close(getMonitor());
			joinAutoRefreshJobs();
			assertEquals("1.1", 0, provider.getMonitoredResources().length);
			project.open(getMonitor());
			joinAutoRefreshJobs();
			assertEquals("1.2", 1, provider.getMonitoredResources().length);
			ensureDoesNotExistInWorkspace(project);
			joinAutoRefreshJobs();
			assertEquals("1.3", 0, provider.getMonitoredResources().length);
			//check provider for other errors
			AssertionFailedError[] failures = provider.getFailures();
			if (failures.length > 0)
				fail("" + failures.length + " failures", failures[0]);
		} catch (CoreException e) {
			fail("1.99", e);
		}
	}

	private void joinAutoRefreshJobs() throws InterruptedException {
		// We must join on the auto-refresh family because the workspace changes done in the 
		// tests above may be batched and broadcasted by the RefreshJob, not the main thread. 
		// There is then a race condition between the main thread, the refresh job and the job 
		// scheduled by MonitorManager.monitorAsync. Thus, we must join on both the RefreshJob 
		// and the job scheduled by MonitorManager.monitorAsync. For simplicity, the job 
		// scheduled by MonitorManager.monitorAsync has been set to belong to the same family 
		// as the RefreshJob. 
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, null);
	}
}
