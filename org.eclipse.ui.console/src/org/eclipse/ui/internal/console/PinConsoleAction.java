/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.console;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.texteditor.IUpdate;

/**
 * Pins the currently visible console in a console view.
 */
public class PinConsoleAction extends Action implements IUpdate {
	
	private IConsoleView fView = null;

	/**
	 * Constructs a 'pin console' action
	 */
	public PinConsoleAction(IConsoleView view) {
		super(ConsoleMessages.getString("PinConsoleAction.0"), IAction.AS_CHECK_BOX); //$NON-NLS-1$
		setToolTipText(ConsoleMessages.getString("PinConsoleAction.1")); //$NON-NLS-1$
		setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_PIN));
		setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_PIN));
		setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_LCL_PIN));
		fView = view;
		update();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	public void run() {
		if (isChecked()) {
			fView.pin(fView.getConsole());
		} else {
			fView.pin(null);
		}
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.IUpdate#update()
	 */
	public void update() {
		setEnabled(fView.getConsole() != null);
		setChecked(fView.isPinned());
	}

}
