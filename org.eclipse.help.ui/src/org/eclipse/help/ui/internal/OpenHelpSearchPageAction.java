package org.eclipse.help.ui.internal;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.search.ui.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;

/**
 * Opens the Search Dialog and brings the Help search page to front
 */
public class OpenHelpSearchPageAction
	implements IWorkbenchWindowActionDelegate {

	private static final String HELP_SEARCH_PAGE_ID = WorkbenchHelpPlugin.PLUGIN_ID+".searchPage"; //$NON-NLS-1$

	private IWorkbenchWindow fWindow;

	public OpenHelpSearchPageAction() {
	}

	public void init(IWorkbenchWindow window) {
		fWindow = window;
	}

	public void run(IAction action) {
		if (fWindow == null || fWindow.getActivePage() == null) {
			beep();
			WorkbenchHelpPlugin.logError("Could not open the search dialog - for some reason the window handle was null", null); //$NON-NLS-1$
			return;
		}
		SearchUI.openSearchDialog(fWindow, HELP_SEARCH_PAGE_ID);
	}

	public void selectionChanged(IAction action, ISelection selection) {
		// do nothing since the action isn't selection dependent.
	}

	public void dispose() {
		fWindow = null;
	}

	protected void beep() {
		Shell shell = null;
		IWorkbenchWindow window =
			PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			shell = window.getShell();
		}
		if (shell != null && shell.getDisplay() != null)
			shell.getDisplay().beep();
	}
}