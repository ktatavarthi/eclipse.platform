/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.debug.internal.ui.views.memory;

import java.util.Vector;
import org.eclipse.debug.core.model.IMemoryBlockRetrieval;
import org.eclipse.debug.internal.core.memory.IExtendedMemoryBlockRetrieval;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.help.WorkbenchHelp;

/**
 * @since 3.0
 */
public class MonitorMemoryBlockDialog extends Dialog implements ModifyListener{

	private static Vector history = new Vector();
	private Combo expressionInput;
	private Text lengthInput;
	private String expression;
	private String length;
	private boolean needLength = true;
	
	private static final String PREFIX = "MonitorMemoryBlockDialog."; //$NON-NLS-1$
	private static final String ENTER_EXPRESSION = PREFIX + "EnterExpressionToMonitor"; //$NON-NLS-1$
	private static final String MONITOR_MEMORY = PREFIX + "MonitorMemory"; //$NON-NLS-1$
	private static final String NUMBER_OF_BYTES = PREFIX + "NumberOfBytes"; //$NON-NLS-1$
	

	/**
	 * @param parentShell
	 */
	public MonitorMemoryBlockDialog(Shell parentShell, IMemoryBlockRetrieval memRetrieval) {
		super(parentShell);
		
		if (memRetrieval instanceof IExtendedMemoryBlockRetrieval)
			needLength = false;
		
		WorkbenchHelp.setHelp(parentShell, IDebugUIConstants.PLUGIN_ID + ".MonitorMemoryBlockDialog_context"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createDialogArea(Composite parent) {
	
		parent.setLayout(new GridLayout());
		GridData spec2= new GridData();
		spec2.grabExcessVerticalSpace= true;
		spec2.grabExcessHorizontalSpace= true;
		spec2.horizontalAlignment= GridData.FILL;
		spec2.verticalAlignment= GridData.CENTER;
		parent.setLayoutData(spec2);

		Label textLabel = new Label(parent, SWT.NONE);
		textLabel.setText(DebugUIMessages.getString(ENTER_EXPRESSION));
		GridData textLayout = new GridData();
		textLayout.widthHint = 280;
		textLabel.setLayoutData(textLayout);
		
		expressionInput = new Combo(parent, SWT.BORDER);
		GridData spec= new GridData();
		spec.grabExcessVerticalSpace= false;
		spec.grabExcessHorizontalSpace= true;
		spec.horizontalAlignment= GridData.FILL;
		spec.verticalAlignment= GridData.BEGINNING;
		spec.heightHint = 50;
		expressionInput.setLayoutData(spec);
		
		// add history
		String[] historyExpression = (String[])history.toArray(new String[history.size()]);
		for (int i=0; i<historyExpression.length; i++)
		{
			expressionInput.add(historyExpression[i]);
		}
		
		expressionInput.addModifyListener(this);
		
		if (needLength)
		{
			Label lengthLabel = new Label(parent, SWT.NONE);
			lengthLabel.setText(DebugUIMessages.getString(NUMBER_OF_BYTES));
			GridData lengthLayout = new GridData();
			lengthLayout.widthHint = 280;
			lengthLabel.setLayoutData(lengthLayout);
			
			lengthInput = new Text(parent, SWT.BORDER);
			GridData lengthSpec= new GridData();
			lengthSpec.grabExcessVerticalSpace= false;
			lengthSpec.grabExcessHorizontalSpace= true;
			lengthSpec.horizontalAlignment= GridData.FILL;
			lengthInput.setLayoutData(lengthSpec);
			lengthInput.addModifyListener(this);
		}
		return parent;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
	 */
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		
		newShell.setText(DebugUIMessages.getString(MONITOR_MEMORY));
	}
	
	public String getExpression()
	{
		return expression;
	}
	
	public String getLength()
	{
		return length;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#okPressed()
	 */
	protected void okPressed() {

		expression = expressionInput.getText();

		// add to history list
		if (!history.contains(expression))
			history.insertElementAt(expression, 0);

		if (needLength)
			length = lengthInput.getText();

		super.okPressed();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
	 */
	public void modifyText(ModifyEvent e) {

		if (needLength)
		{
			String length = lengthInput.getText();
			String input = expressionInput.getText();
			
			if (input == null || input.equals("") || length == null || length.equals("")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				getButton(IDialogConstants.OK_ID).setEnabled(false);	
			}
			else
			{
				getButton(IDialogConstants.OK_ID).setEnabled(true);
			}			
		}
		else
		{
			String input = expressionInput.getText();
		
			if (input == null || input.equals("")) //$NON-NLS-1$
			{
				getButton(IDialogConstants.OK_ID).setEnabled(false);	
			}
			else
			{
				getButton(IDialogConstants.OK_ID).setEnabled(true);
			}			
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createButtonBar(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createButtonBar(Composite parent) {
		
		Control ret =  super.createButtonBar(parent);
		getButton(IDialogConstants.OK_ID).setEnabled(false);
		
		return ret;
	}

}
