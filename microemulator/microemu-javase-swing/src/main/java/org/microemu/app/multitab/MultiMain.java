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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * The host window holding a Chrome-like tab strip where every tab runs an
 * isolated emulator session.
 */
public class MultiMain extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final Color SELECTED_TAB_BG = new Color(0xE8F0FE);

	private static final Color NORMAL_TAB_BG = new Color(0xD9D9D9);

	/** Pixels to scroll per mouse wheel unit over the tab strip. */
	private static final int WHEEL_SCROLL_STEP = 40;

	/** Horizontal strip holding the tab buttons followed by the "+" button. */
	private final JPanel tabStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));

	/**
	 * Scrolls {@link #tabStrip} horizontally when there are more tabs than fit
	 * in the window; the scroll bars themselves are hidden and navigation is
	 * done with the {@code <} / {@code >} buttons.
	 */
	private final JScrollPane tabScroll = new JScrollPane(tabStrip,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

	/** Area showing the content of the currently selected tab. */
	private final JPanel contentArea = new JPanel(new BorderLayout());

	private final JButton addButton = new JButton("+");

	private final JButton scrollLeftButton = new JButton("<");

	private final JButton scrollRightButton = new JButton(">");

	private final List<SessionHolder> sessions = new ArrayList<SessionHolder>();

	private SessionHolder active;

	private int tabCounter = 0;

	private static class SessionHolder {
		EmulatorSession session;
		SessionClassLoader loader;
		JComponent content;
		JPanel tabComponent;
		JLabel titleLabel;
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
				if (active != null) {
					active.session.pauseMIDlet();
				}
			}

			public void windowDeiconified(WindowEvent e) {
				if (active != null) {
					active.session.resumeMIDlet();
				}
			}
		});

		addButton.setMargin(new Insets(0, 6, 0, 6));
		addButton.setFocusable(false);
		addButton.setToolTipText("New tab");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addSession(new String[0]);
			}
		});

		tabStrip.add(addButton);

		tabScroll.setBorder(null);
		tabScroll.setWheelScrollingEnabled(false);
		tabScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
		tabScroll.getViewport().addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				updateScrollButtons();
			}
		});
		tabScroll.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				updateScrollButtons();
			}
		});
		tabStrip.addMouseWheelListener(new MouseAdapter() {
			public void mouseWheelMoved(MouseWheelEvent e) {
				JViewport viewport = tabScroll.getViewport();
				Point position = viewport.getViewPosition();
				int max = Math.max(0, tabStrip.getWidth() - viewport.getExtentSize().width);
				int newX = position.x + e.getUnitsToScroll() * WHEEL_SCROLL_STEP;
				newX = Math.max(0, Math.min(newX, max));
				viewport.setViewPosition(new Point(newX, position.y));
			}
		});

		scrollLeftButton.setMargin(new Insets(0, 4, 0, 4));
		scrollLeftButton.setFocusable(false);
		scrollLeftButton.setToolTipText("Scroll tabs left");
		scrollLeftButton.setEnabled(false);
		scrollLeftButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				scrollTabs(false);
			}
		});

		scrollRightButton.setMargin(new Insets(0, 4, 0, 4));
		scrollRightButton.setFocusable(false);
		scrollRightButton.setToolTipText("Scroll tabs right");
		scrollRightButton.setEnabled(false);
		scrollRightButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				scrollTabs(true);
			}
		});

		JPanel scrollButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
		scrollButtons.add(scrollLeftButton);
		scrollButtons.add(scrollRightButton);

		JPanel scrollButtonsWrap = new JPanel(new BorderLayout());
		scrollButtonsWrap.add(scrollButtons, BorderLayout.NORTH);

		JPanel top = new JPanel(new BorderLayout());
		top.add(tabScroll, BorderLayout.CENTER);
		top.add(scrollButtonsWrap, BorderLayout.EAST);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(top, BorderLayout.NORTH);
		getContentPane().add(contentArea, BorderLayout.CENTER);

		setSize(480, 700);
		setLocationByPlatform(true);
	}

	private String nextTitle(EmulatorSession session) {
		String title = session.getTitle();
		if (title == null || title.length() == 0 || "MicroEmulator".equals(title)) {
			title = "Tab " + tabCounter;
		}
		return title;
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
			session.setTitleChangeListener(new Runnable() {
				public void run() {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							refreshTabTitles();
						}
					});
				}
			});

			final SessionHolder holder = new SessionHolder();
			holder.session = session;
			holder.loader = loader;
			holder.content = session.getContent();

			tabCounter++;
			holder.tabComponent = createTabComponent(holder, nextTitle(session));

			// Insert the tab before the "+" button so the button always sits
			// right next to the last tab.
			tabStrip.remove(addButton);
			tabStrip.add(holder.tabComponent);
			tabStrip.add(addButton);
			tabStrip.revalidate();
			tabStrip.repaint();

			sessions.add(holder);

			selectSession(holder);

			session.initSession(args);

			refreshTabTitles();
			updateScrollButtonsSoon();
		} catch (Throwable e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Unable to start emulator session: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private JPanel createTabComponent(final SessionHolder holder, String title) {
		final JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		tab.setOpaque(true);
		tab.setBackground(NORMAL_TAB_BG);
		tab.setBorder(BorderFactory.createLineBorder(NORMAL_TAB_BG.darker()));

		final JLabel titleLabel = new JLabel(title);
		tab.add(titleLabel);

		final JButton closeButton = new JButton("x");
		closeButton.setMargin(new Insets(0, 3, 0, 3));
		closeButton.setFocusable(false);
		closeButton.setToolTipText("Close tab");
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				closeSession(holder);
			}
		});
		tab.add(closeButton);

		MouseAdapter selectListener = new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				selectSession(holder);
			}
		};
		tab.addMouseListener(selectListener);
		titleLabel.addMouseListener(selectListener);

		Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
		tab.setCursor(hand);
		titleLabel.setCursor(hand);

		holder.titleLabel = titleLabel;
		return tab;
	}

	private void selectSession(SessionHolder holder) {
		if (holder == null || holder == active) {
			return;
		}
		active = holder;

		contentArea.removeAll();
		contentArea.add(holder.content, BorderLayout.CENTER);
		contentArea.revalidate();
		contentArea.repaint();

		setJMenuBar(holder.session.getMenuBar());

		updateTabVisuals();
		holder.content.requestFocusInWindow();

		final SessionHolder selected = holder;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				scrollTabToVisible(selected);
				updateScrollButtons();
			}
		});
	}

	private void updateTabVisuals() {
		for (int i = 0; i < sessions.size(); i++) {
			SessionHolder holder = sessions.get(i);
			boolean selected = (holder == active);
			holder.tabComponent.setBackground(selected ? SELECTED_TAB_BG : NORMAL_TAB_BG);
			Color line = selected ? SELECTED_TAB_BG.darker() : NORMAL_TAB_BG.darker();
			holder.tabComponent.setBorder(BorderFactory.createLineBorder(line));
		}
	}

	private void refreshTabTitles() {
		for (int i = 0; i < sessions.size(); i++) {
			SessionHolder holder = sessions.get(i);
			String title = holder.session.getTitle();
			if (title != null && title.length() > 0 && !"MicroEmulator".equals(title) && holder.titleLabel != null) {
				holder.titleLabel.setText(title);
			}
		}
		tabStrip.revalidate();
		updateScrollButtonsSoon();
	}

	private void closeSession(SessionHolder holder) {
		int index = sessions.indexOf(holder);
		if (index == -1) {
			return;
		}

		tabStrip.remove(holder.tabComponent);
		sessions.remove(index);

		try {
			holder.session.disposeSession();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		// Drop all references so that the session class loader can be collected
		holder.session = null;
		holder.content = null;
		holder.tabComponent = null;
		holder.titleLabel = null;
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
			return;
		}

		if (active == holder) {
			active = null;
			SessionHolder next = sessions.get(Math.min(index, sessions.size() - 1));
			selectSession(next);
		}

		tabStrip.revalidate();
		tabStrip.repaint();
		refreshTabTitles();
		updateScrollButtonsSoon();
	}

	/**
	 * Scrolls the tab strip so that the next hidden tab (right) or the previous
	 * hidden tab (left) becomes visible, one tab at a time.
	 */
	private void scrollTabs(boolean right) {
		JViewport viewport = tabScroll.getViewport();
		Point position = viewport.getViewPosition();
		int extent = viewport.getExtentSize().width;
		int max = Math.max(0, tabStrip.getWidth() - extent);
		int newX = position.x;

		if (right) {
			int visibleRight = position.x + extent;
			for (int i = 0; i < sessions.size(); i++) {
				Rectangle bounds = sessions.get(i).tabComponent.getBounds();
				if (bounds.x + bounds.width > visibleRight) {
					newX = bounds.x + bounds.width - extent;
					break;
				}
			}
			if (newX <= position.x) {
				newX = position.x + extent;
			}
		} else {
			for (int i = sessions.size() - 1; i >= 0; i--) {
				Rectangle bounds = sessions.get(i).tabComponent.getBounds();
				if (bounds.x < position.x) {
					newX = bounds.x;
					break;
				}
			}
			if (newX >= position.x) {
				newX = position.x - extent;
			}
		}

		newX = Math.max(0, Math.min(newX, max));
		viewport.setViewPosition(new Point(newX, position.y));
		updateScrollButtons();
	}

	private void scrollTabToVisible(SessionHolder holder) {
		if (holder == null || holder.tabComponent == null) {
			return;
		}
		Rectangle bounds = holder.tabComponent.getBounds();
		if (bounds.width == 0 || bounds.height == 0) {
			return;
		}
		holder.tabComponent.scrollRectToVisible(bounds);
	}

	private void updateScrollButtons() {
		JViewport viewport = tabScroll.getViewport();
		int extent = viewport.getExtentSize().width;
		int max = Math.max(0, tabStrip.getWidth() - extent);
		int x = viewport.getViewPosition().x;
		scrollLeftButton.setEnabled(x > 0);
		scrollRightButton.setEnabled(x < max);
	}

	private void updateScrollButtonsSoon() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				updateScrollButtons();
			}
		});
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
		// Size the window to fit the first session's device.
		pack();
		updateScrollButtonsSoon();
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
