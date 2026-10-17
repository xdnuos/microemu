/**
 *  MicroEmulator
 *  Copyright (C) 2006-2007 Bartek Teodorczyk <barteo@barteo.net>
 *  Copyright (C) 2006-2007 Michael Lifshits
 *
 *  It is licensed under the following two licenses as alternatives:
 *    1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 *    2. Apache License (the "AL") Version 2.0
 *
 *  You may not use this file except in compliance with at least one of
 *  the above two licenses.
 *
 *  You may obtain a copy of the LGPL at
 *      http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 *  You may obtain a copy of the AL at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the LGPL or the AL for the specific language governing permissions and
 *  limitations.
 *
 *  @version $Id$
 */
package org.microemu.app.ui.swing.logconsole;

/**
 * @author Michael Lifshits
 *
 */
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JViewport;

public class LogTextArea extends JTextArea {

	private static final long serialVersionUID = 1L;

	//private int maxLines = 20;

	private LogTextCaret caret;

	public LogTextArea(int rows, int columns, int maxLines) {
		super(rows, columns);
		caret = new LogTextCaret();
		setCaret(caret);
		//this.maxLines = maxLines;
		setEditable(false);
		// A non-editable JTextComponent is not focusable by default, which
		// prevents selecting text and copying it (Ctrl+C). Keep it focusable
		// so the log can be selected and copied.
		setFocusable(true);

		JPopupMenu popup = new JPopupMenu();
		JMenuItem copyItem = new JMenuItem("Copy");
		copyItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				copy();
			}
		});
		popup.add(copyItem);
		JMenuItem selectAllItem = new JMenuItem("Select All");
		selectAllItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectAll();
			}
		});
		popup.add(selectAllItem);
		setComponentPopupMenu(popup);
	}

	public void setText(String t) {
		super.setText(t);
		caret.setVisibilityAdjustment(true);
	}

	public void append(String str) {

		super.append(str);

		// While the user has a selection (e.g. to copy the log), do not move
		// the caret or the viewport, otherwise the selection would be lost
		// every time a new log line arrives.
		if (getSelectionStart() != getSelectionEnd()) {
			caret.setVisibilityAdjustment(false);
			return;
		}

		JViewport viewport = (JViewport) getParent();
		boolean scrollToBottom = Math.abs(viewport.getViewPosition().getY() - (getHeight() - viewport.getHeight())) < 100;

		caret.setVisibilityAdjustment(scrollToBottom);
		
		if (scrollToBottom) {
			setCaretPosition(getText().length());
		}

		//		if (getLineCount() > maxLines) {
		//			Document doc = getDocument();
		//			if (doc != null) {
		//				try {
		//					doc.remove(0, getLineStartOffset(getLineCount() - maxLines - 1));
		//				} catch (BadLocationException e) {
		//				}
		//			}
		//			if (!scrollToBottom) {
		//				Rectangle nloc = new Rectangle(0,30,10,10);
		//		        if (SwingUtilities.isEventDispatchThread()) {
		//		        	scrollRectToVisible(nloc);
		//		        } else {
		//		            SwingUtilities.invokeLater(new SafeScroller(nloc));
		//		        }
		//			}
		//		}
	}

	class SafeScroller implements Runnable {

		Rectangle r;

		SafeScroller(Rectangle r) {
			this.r = r;
		}

		public void run() {
			LogTextArea.this.scrollRectToVisible(r);
		}
	}

}