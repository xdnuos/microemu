/**
 *  MicroEmulator
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 *
 *  This interface is the only contract shared between the host tab container
 *  (loaded by the system class loader) and a single emulator session
 *  (loaded by its own {@code SessionClassLoader}). It therefore must only use
 *  types from the Java standard library.
 */
package org.microemu.app.multitab;

import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JMenuBar;

/**
 * A single, isolated emulator instance hosted inside one tab.
 */
public interface EmulatorSession {

	/**
	 * @return the component to be placed as tab content (the device panel and
	 *         status bar).
	 */
	JComponent getContent();

	/**
	 * @return the menu bar for this session. The host installs it on the main
	 *         window while this session's tab is active.
	 */
	JMenuBar getMenuBar();

	/**
	 * @return a human readable title for the tab (typically the MIDlet suite
	 *         name once loaded).
	 */
	String getTitle();

	/**
	 * Sets the top level window that owns this session (used as parent for
	 * dialogs).
	 */
	void setOwnerWindow(Window owner);

	/**
	 * Registers a callback invoked when the user requests the application to
	 * exit from this session's menu.
	 */
	void setExitHandler(Runnable handler);

	/**
	 * Builds the device and starts the MIDlet (if any) described by the
	 * command line arguments.
	 */
	void initSession(String[] args);

	/**
	 * Loads and starts a MIDlet from the given jad/jar URL in this session.
	 */
	void openMIDlet(String url);

	/**
	 * Closes the currently running MIDlet and returns to the launcher.
	 */
	void closeCurrentMidlet();

	/**
	 * @return the device descriptor location currently in use.
	 */
	String getDeviceDescriptor();

	/**
	 * Switches this session to the device identified by the descriptor
	 * location (used to keep the device selection shared across tabs).
	 */
	void applyDeviceDescriptor(String descriptorLocation);

	/**
	 * Registers a callback invoked whenever the device was changed from this
	 * session, so that the host can propagate the change to other sessions.
	 */
	void setDeviceChangeListener(Runnable listener);

	/**
	 * Pauses the MIDlet (window minimized).
	 */
	void pauseMIDlet();

	/**
	 * Resumes the MIDlet (window restored).
	 */
	void resumeMIDlet();

	/**
	 * Destroys the MIDlet, disposes any dialogs and releases resources so that
	 * this session's class loader can be garbage collected.
	 */
	void disposeSession();

}
