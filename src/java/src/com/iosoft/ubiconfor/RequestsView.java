package com.iosoft.ubiconfor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import com.iosoft.helpers.HtmlEscape;
import com.iosoft.helpers.ui.awt.AntialiasedEditorPane;
import com.iosoft.helpers.ui.awt.MiscAWT;
import com.iosoft.ubiconfor.dtos.RequestDataDto;
import com.iosoft.ubiconfor.dtos.ResponseDataDto;

public class RequestsView {

	public final JComponent Panel;
	private final JEditorPane _editorPane;
	private final JScrollPane _scrollPane;

	private final List<String> _entries = new ArrayList<>();

	public RequestsView() {
		_editorPane = new AntialiasedEditorPane("text/html", "");
		_editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		_editorPane.setEditable(false);
		_editorPane.setOpaque(false);
		_editorPane.setForeground(Color.BLACK);
		_editorPane.setBackground(Color.WHITE);
		_scrollPane = new JScrollPane(_editorPane);
		_scrollPane.setOpaque(false);
		_scrollPane.getViewport().setOpaque(false);
		_scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		_scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		_scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
		Panel = _scrollPane;
	}

	public void add(RequestDataDto request, ResponseDataDto response, Exception e) {
		String entry = HtmlEscape.sanitizeForLabel(request.Method + " " + request.Uri) + "<br>\n-> "
				+ HtmlEscape.sanitizeForLabel(
						e == null ? (response.StatusCode + " (" + response.Content.length + " bytes)") : e.toString())
				+ "<br>\n<br>\n";
		_entries.add(entry);

		rebuild();
	}

	private void rebuild() {
		StringBuilder sb = new StringBuilder();
		sb.append("<html><body>");
		sb.append(_entries.size());
		sb.append(" entries<br><br>\n\n");
		for (String entry : _entries) {
			sb.append(entry);
		}
		sb.append("</body></html>");

		_editorPane.setText(sb.toString());
		scrollDown();
		_scrollPane.revalidate();
		_scrollPane.repaint();
	}

	private void scrollDown() {
		// TODO: Replace with UpdateCaret.Always?
		MiscAWT.scrollDownContent(_editorPane);
	}
}
