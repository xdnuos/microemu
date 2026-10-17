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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The host window holding a Chrome-like tab strip where every tab runs an
 * isolated emulator session.
 */
public class MultiMain extends JFrame {

	private static final long serialVersionUID = 1L;

	private final JTabbedPane tabbedPane = new JTabbedPane();

	private final List<SessionHolder> sessions = new ArrayList<SessionHolder>();

	private int tabCounter = 0;

	private static class SessionHolder {
		EmulatorSession session;
		SessionClassLoader loader;
		JComponent content;
	}

	public MultiMain() {
		super("MicroEmulator");

		try {
			setIconImage(Toolkit.getDefaultToolkit().getImage(MultiMain.class.getResource("/org/microemu/icon.png")));
		} catch (Throwable e) {
			// ignore missing icon
		}

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit();
			}

			public void windowIconified(WindowEvent e) {
				EmulatorSession session = getActiveSession();
				if (session != null) {
					session.pauseMIDlet();
				}
			}

			public void windowDeiconified(WindowEvent e) {
				EmulatorSession session = getActiveSession();
				if (session != null) {
					session.resumeMIDlet();
				}
			}
		});

		final JButton addButton = new JButton("+");
		addButton.setMargin(new Insets(0, 6, 0, 6));
		addButton.setFocusable(false);
		addButton.setToolTipText("New tab");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addSession(new String[0]);
			}
		});

		JPanel addPanel = new JPanel(new BorderLayout());
		addPanel.add(addButton, BorderLayout.NORTH);

		JPanel north = new JPanel(new BorderLayout());
		north.add(tabbedPane, BorderLayout.CENTER);
		north.add(addPanel, BorderLayout.EAST);

		getContentPane().add(north, BorderLayout.CENTER);

		tabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(javax.swing.event.ChangeEvent e) {
				updateActiveUI();
			}
		});

		setSize(480, 700);
		setLocationByPlatform(true);
	}

	private EmulatorSession getActiveSession() {
		int index = tabbedPane.getSelectedIndex();
		if (index < 0 || index >= sessions.size()) {
			return null;
		}
		return sessions.get(index).session;
	}

	private void updateActiveUI() {
		EmulatorSession session = getActiveSession();
		if (session != null) {
			setJMenuBar(session.getMenuBar());
		}
		refreshTabTitles();
	}

	private void refreshTabTitles() {
		for (int i = 0; i < sessions.size() && i < tabbedPane.getTabCount(); i++) {
			JPanel tab = (JPanel) tabbedPane.getTabComponentAt(i);
			if (tab == null) {
				continue;
			}
			JLabel label = (JLabel) tab.getClientProperty("titleLabel");
			if (label != null) {
				label.setText(sessions.get(i).session.getTitle());
			}
		}
	}

	/**
	 * Creates a new isolated emulator session and adds it as a new tab.
	 */
	public void addSession(String[] args) {
		try {
			SessionClassLoader loader = new SessionClassLoader(SessionClassLoader.getSystemClassPathURLs(),
					MultiMain.class.getClassLoader());
			Class<?> mainClass = loader.loadClass("org.microemu.app.Main");
			final EmulatorSession session = (EmulatorSession) mainClass.newInstance();
			session.setOwnerWindow(this);
			session.setExitHandler(new Runnable() {
				public void run() {
					exit();
				}
			});
			session.setDeviceChangeListener(new Runnable() {
				public void run() {
					propagateDevice(session);
				}
			});

			SessionHolder holder = new SessionHolder();
			holder.session = session;
			holder.loader = loader;
			holder.content = session.getContent();

			sessions.add(holder);
			tabCounter++;
			String title = session.getTitle();
			if (title == null || title.length() == 0) {
				title = "Tab " + tabCounter;
			}
			tabbedPane.addTab(title, holder.content);
			final int index = tabbedPane.getTabCount() - 1;
			tabbedPane.setTabComponentAt(index, createTabComponent(holder.content, index));
			tabbedPane.setSelectedIndex(index);

			session.initSession(args);

			updateActiveUI();
		} catch (Throwable e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Unable to start emulator session: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private JPanel createTabComponent(JComponent content, final int initialIndex) {
		final JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		tab.setOpaque(false);

		final JLabel titleLabel = new JLabel();
		titleLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 6));
		tab.add(titleLabel);
		tab.putClientProperty("titleLabel", titleLabel);

		final JButton closeButton = new JButton("x");
		closeButton.setMargin(new Insets(0, 3, 0, 3));
		closeButton.setFocusable(false);
		closeButton.setToolTipText("Close tab");
		closeButton.putClientProperty("content", content);
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int index = indexOfContent((JComponent) closeButton.getClientProperty("content"));
				if (index != -1) {
					closeSession(index);
				}
			}
		});
		tab.add(closeButton);

		return tab;
	}

	private int indexOfContent(JComponent content) {
		for (int i = 0; i < sessions.size(); i++) {
			if (sessions.get(i).content == content) {
				return i;
			}
		}
		return -1;
	}

	private void closeSession(int index) {
		SessionHolder holder = sessions.remove(index);
		tabbedPane.removeTabAt(index);

		try {
			holder.session.disposeSession();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		// Drop all references so that the session class loader can be collected
		holder.session = null;
		holder.content = null;
		if (holder.loader != null) {
			try {
				holder.loader.close();
			} catch (Throwable e) {
				// ignore
			}
			holder.loader = null;
		}

		if (sessions.isEmpty()) {
			exit();
		} else {
			updateActiveUI();
		}
	}

	private void propagateDevice(EmulatorSession source) {
		String descriptor = source.getDeviceDescriptor();
		if (descriptor == null) {
			return;
		}
		for (int i = 0; i < sessions.size(); i++) {
			EmulatorSession target = sessions.get(i).session;
			if (target != source) {
				try {
					target.applyDeviceDescriptor(descriptor);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void exit() {
		for (int i = 0; i < sessions.size(); i++) {
			try {
				sessions.get(i).session.disposeSession();
			} catch (Throwable e) {
				// ignore
			}
		}
		System.exit(0);
	}

	public void init(String[] args) {
		addSession(args != null ? args : new String[0]);
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// keep default look and feel
		}

		final String[] a = args;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				MultiMain app = new MultiMain();
				app.setVisible(true);
				app.init(a);
			}
		});
	}

}
