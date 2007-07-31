/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.actions;


import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.contextlaunching.LaunchingResourceManager;
import org.eclipse.debug.ui.IDebugUIConstants;

/**
 * Relaunches the last debug-mode launch
 * 
 * This menu item appears in the main 'Run' menu
 * 
 * @see RelaunchLastAction
 * @see RunLastAction
 * @see ProfileLastAction
 */
public class DebugLastAction extends RelaunchLastAction {
	
	/**
	 * @see RelaunchLastAction#getMode()
	 */
	public String getMode() {
		return ILaunchManager.DEBUG_MODE;
	}	
	
	/**
	 * @see org.eclipse.debug.internal.ui.actions.LaunchDropDownAction#getLaunchGroupId()
	 */
	public String getLaunchGroupId() {
		return IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP;
	}

	/**
	 * @see org.eclipse.debug.internal.ui.actions.RelaunchLastAction#getText()
	 */
	protected String getText() {
		if(LaunchingResourceManager.isContextLaunchEnabled()) {
			return ActionMessages.DebugLastAction_1;
		}
		else {
			return ActionMessages.DebugLastAction_0;
		}
	}

	/**
	 * @see org.eclipse.debug.internal.ui.actions.RelaunchLastAction#getTooltipText()
	 */
	protected String getTooltipText() {
		return IInternalDebugUIConstants.EMPTY_STRING;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.actions.RelaunchLastAction#getCommandId()
	 */
	protected String getCommandId() {
		return "org.eclipse.debug.ui.commands.DebugLast"; //$NON-NLS-1$
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.actions.RelaunchLastAction#getDescription()
	 */
	protected String getDescription() {
		if(LaunchingResourceManager.isContextLaunchEnabled()) {
			return ActionMessages.DebugLastAction_2;
		}
		else {
			return ActionMessages.DebugLastAction_3;
		}
	}	
}
