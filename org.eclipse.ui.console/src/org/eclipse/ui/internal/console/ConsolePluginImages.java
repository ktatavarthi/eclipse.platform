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


import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleConstants;

/**
 * The images provided by the debug plugin.
 */
public class ConsolePluginImages {

	/** 
	 * The image registry containing <code>Image</code>s.
	 */
	private static ImageRegistry imageRegistry;
	
	/**
	 * A table of all the <code>ImageDescriptor</code>s.
	 */
	private static HashMap imageDescriptors;
	
	/* Declare Common paths */
	private static URL ICON_BASE_URL= null;

	static {
		String pathSuffix = "icons/full/"; //$NON-NLS-1$
			
		try {
			ICON_BASE_URL= new URL(ConsolePlugin.getDefault().getDescriptor().getInstallURL(), pathSuffix);
		} catch (MalformedURLException e) {
			// do nothing
		}
	}

	// Use IPath and toOSString to build the names to ensure they have the slashes correct
	private final static String LOCALTOOL= "clcl16/"; //basic colors - size 16x16 //$NON-NLS-1$
	private final static String DLCL= "dlcl16/"; //disabled - size 16x16 //$NON-NLS-1$
	private final static String ELCL= "elcl16/"; //enabled - size 16x16 //$NON-NLS-1$
	private final static String VIEW= "cview16/"; // views //$NON-NLS-1$
	
	/**
	 * Declare all images
	 */
	private static void declareImages() {
		// Actions
		
		//local toolbars
		declareRegistryImage(IConsoleConstants.IMG_LCL_CLEAR, LOCALTOOL + "clear_co.gif"); //$NON-NLS-1$
		declareRegistryImage(IInternalConsoleConstants.IMG_LCL_PIN, LOCALTOOL + "pin.gif"); //$NON-NLS-1$
			
		// disabled local toolbars
		declareRegistryImage(IInternalConsoleConstants.IMG_DLCL_CLEAR, DLCL + "clear_co.gif"); //$NON-NLS-1$
		declareRegistryImage(IInternalConsoleConstants.IMG_DLCL_PIN, DLCL + "pin.gif"); //$NON-NLS-1$
		
		// enabled local toolbars
		declareRegistryImage(IInternalConsoleConstants.IMG_ELCL_CLEAR, ELCL + "clear_co.gif"); //$NON-NLS-1$
		declareRegistryImage(IInternalConsoleConstants.IMG_ELCL_PIN, ELCL + "pin.gif"); //$NON-NLS-1$
		
		// Views
		declareRegistryImage(IConsoleConstants.IMG_VIEW_CONSOLE, VIEW + "console_view.gif"); //$NON-NLS-1$				
	}

	/**
	 * Declare an Image in the registry table.
	 * @param key 	The key to use when registering the image
	 * @param path	The path where the image can be found. This path is relative to where
	 *				this plugin class is found (i.e. typically the packages directory)
	 */
	private final static void declareRegistryImage(String key, String path) {
		ImageDescriptor desc= ImageDescriptor.getMissingImageDescriptor();
		try {
			desc= ImageDescriptor.createFromURL(makeIconFileURL(path));
		} catch (MalformedURLException me) {
			ConsolePlugin.log(me);
		}
		imageRegistry.put(key, desc);
		imageDescriptors.put(key, desc);
	}
	
	/**
	 * Returns the ImageRegistry.
	 */
	public static ImageRegistry getImageRegistry() {
		if (imageRegistry == null) {
			initializeImageRegistry();
		}
		return imageRegistry;
	}

	/**
	 *	Initialize the image registry by declaring all of the required
	 *	graphics. This involves creating JFace image descriptors describing
	 *	how to create/find the image should it be needed.
	 *	The image is not actually allocated until requested.
	 *
	 * 	Prefix conventions
	 *		Wizard Banners			WIZBAN_
	 *		Preference Banners		PREF_BAN_
	 *		Property Page Banners	PROPBAN_
	 *		Color toolbar			CTOOL_
	 *		Enable toolbar			ETOOL_
	 *		Disable toolbar			DTOOL_
	 *		Local enabled toolbar	ELCL_
	 *		Local Disable toolbar	DLCL_
	 *		Object large			OBJL_
	 *		Object small			OBJS_
	 *		View 					VIEW_
	 *		Product images			PROD_
	 *		Misc images				MISC_
	 *
	 *	Where are the images?
	 *		The images (typically gifs) are found in the same location as this plugin class.
	 *		This may mean the same package directory as the package holding this class.
	 *		The images are declared using this.getClass() to ensure they are looked up via
	 *		this plugin class.
	 *	@see JFace's ImageRegistry
	 */
	public static ImageRegistry initializeImageRegistry() {
		imageRegistry= new ImageRegistry(ConsolePlugin.getStandardDisplay());
		imageDescriptors = new HashMap(30);
		declareImages();
		return imageRegistry;
	}

	/**
	 * Returns the <code>Image<code> identified by the given key,
	 * or <code>null</code> if it does not exist.
	 */
	public static Image getImage(String key) {
		return getImageRegistry().get(key);
	}
	
	/**
	 * Returns the <code>ImageDescriptor<code> identified by the given key,
	 * or <code>null</code> if it does not exist.
	 */
	public static ImageDescriptor getImageDescriptor(String key) {
		if (imageDescriptors == null) {
			initializeImageRegistry();
		}
		return (ImageDescriptor)imageDescriptors.get(key);
	}
	
	private static URL makeIconFileURL(String iconPath) throws MalformedURLException {
		if (ICON_BASE_URL == null) {
			throw new MalformedURLException();
		}
			
		return new URL(ICON_BASE_URL, iconPath);
	}
}


