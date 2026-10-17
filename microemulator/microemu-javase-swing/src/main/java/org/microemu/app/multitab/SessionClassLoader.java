/**
 *  MicroEmulator
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 */
package org.microemu.app.multitab;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A class loader that gives every emulator tab its very own copy of the
 * emulator runtime classes.
 * <p>
 * MicroEmulator keeps most of its state in static fields (such as
 * {@code DeviceFactory}, {@code MIDletBridge}, {@code ImplFactory}, ...).
 * Loading the emulator classes again with this child-first class loader turns
 * all those statics into per-session state without touching the core code.
 * <p>
 * Only the classes/interfaces belonging to the host (i.e.
 * {@code org.microemu.app.multitab}) as well as the Java standard library
 * (including Swing) are delegated to the parent class loader.
 */
public class SessionClassLoader extends URLClassLoader {

	/** Packages loaded child-first (i.e. one copy per session). */
	private static final String[] CHILD_FIRST = { "org.microemu.", "javax.microedition." };

	/** Packages always delegated to the parent (shared host code). */
	private static final String[] PARENT_FIRST = { "org.microemu.app.multitab." };

	public SessionClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	/**
	 * @return the URLs of the currently running application class path.
	 */
	public static URL[] getSystemClassPathURLs() {
		String classPath = System.getProperty("java.class.path", "");
		String separator = System.getProperty("path.separator", File.pathSeparator);
		List<URL> urls = new ArrayList<URL>();
		if (classPath.length() > 0) {
			String[] entries = classPath.split(Pattern.quote(separator));
			for (int i = 0; i < entries.length; i++) {
				if (entries[i].length() == 0) {
					continue;
				}
				try {
					urls.add(new File(entries[i]).toURI().toURL());
				} catch (Exception e) {
					// ignore malformed class path entry
				}
			}
		}
		return urls.toArray(new URL[urls.size()]);
	}

	private static boolean isChildFirst(String name) {
		for (int i = 0; i < PARENT_FIRST.length; i++) {
			if (name.startsWith(PARENT_FIRST[i])) {
				return false;
			}
		}
		for (int i = 0; i < CHILD_FIRST.length; i++) {
			if (name.startsWith(CHILD_FIRST[i])) {
				return true;
			}
		}
		return false;
	}

	private static boolean isChildFirstResource(String name) {
		if (name.startsWith("org/microemu/app/multitab/")) {
			return false;
		}
		return name.startsWith("org/microemu/") || name.startsWith("javax/microedition/");
	}

	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// The standard parent-first lookup already covers java.*, javax.swing.*
		// and the host packages; override only to load the emulator packages
		// child-first.
		if (!isChildFirst(name)) {
			return super.loadClass(name, resolve);
		}

		Class<?> result = findLoadedClass(name);
		if (result == null) {
			try {
				result = findClass(name);
			} catch (ClassNotFoundException e) {
				result = null;
			}
		}
		if (result == null) {
			result = super.loadClass(name, false);
		}
		if (resolve) {
			resolveClass(result);
		}
		return result;
	}

	public URL getResource(String name) {
		if (isChildFirstResource(name)) {
			URL url = findResource(name);
			if (url != null) {
				return url;
			}
		}
		return super.getResource(name);
	}

	public Enumeration<URL> getResources(String name) throws IOException {
		if (isChildFirstResource(name)) {
			Enumeration<URL> local = findResources(name);
			if (local != null && local.hasMoreElements()) {
				return local;
			}
		}
		return super.getResources(name);
	}

}
