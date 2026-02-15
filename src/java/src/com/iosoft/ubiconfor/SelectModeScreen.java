package com.iosoft.ubiconfor;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.iosoft.helpers.WrapException;

public class SelectModeScreen {
	public final JPanel SelectionDialog, ChooseRemoteDialog;

	private final Consumer<Mode> _onSelected;

	public SelectModeScreen(Consumer<Mode> onSelected, String exePath) {
		_onSelected = onSelected;
		SelectionDialog = new JPanel(new BorderLayout());
		SelectionDialog.setOpaque(false);
		// Component.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JLabel labelTitle = new JLabel(
				"<html>Make sure that Ubisoft Connect is closed and that no other instances of this tool (javaw.exe in Task Manager) are running.<br><br>Then select a mode of operation:</html>");
		// labelTitle.setHorizontalAlignment(JLabel.CENTER);
		// Font font = labelTitle.getFont();
		// labelTitle.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.2f));
		labelTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		SelectionDialog.add(labelTitle, BorderLayout.NORTH);

		JPanel panelList = new JPanel();
		panelList.setOpaque(false);
		panelList.setLayout(new BoxLayout(panelList, BoxLayout.Y_AXIS));
		SelectionDialog.add(panelList, BorderLayout.CENTER);

		addButton(panelList, Mode.Normal, "Normal",
				"Use the redirect and proxy on this machine locally. This is the normal mode of operation, try this one first. It should always work. It will host the proxy on the local machine and create a temporary hosts file entry.");
		addButton(panelList, Mode.UseProxy, "Special: Use another machine as Proxy",
				"Use another machine in your network as the proxy server. You must run the ubisoftconnect-win7fix on the other machine as well and choose the &quot;Provide Proxy&quot; mode. This will create a temporary hosts file entry to redirect to the other machine.");
		addButton(panelList, Mode.ProvideProxy, "Special: Provide Proxy",
				"Run a proxy server on this machine, but do not use that proxy yourself (no hosts file entry).");
		if (exePath != null) {
			panelList.add(Box.createVerticalStrut(30));
			String name = "tls12only.reg";
			createButton(panelList, "Apply TLS 1.2 Registry Fix", "This will execute the " + name
					+ " file to enforce TLS 1.2 usage in Windows 7 (instead of SSL or TLS &lt;1.2. If you experience problems, give it a try.",
					evt -> {
						try {
							Desktop.getDesktop().open(new File(new File(exePath), name));
						} catch (IOException e) {
							throw new WrapException(e);
						}
					});
		}

		ChooseRemoteDialog = new JPanel(new BorderLayout());

		//SelectionDialog = new JPanel(new BorderLayout());
		//SelectionDialog.setOpaque(false);

		// TODO: We can actually do a TCP IP+port 443 connect test first, and if that
		// works, we ask the .NET app to make a HTTP request.
		// TODO: Before doing the input, start the exe to handle the certificate.
		// TODO: Input remote IP (not hostname, we need to put this into hosts file!)
		// TODO: Perform a test by calling the EXE, which will modify the hosts file,
		// and then try to connect to that.
		// - Try to call a HTTP endpoint there, and if the correct result comes back, it
		// works and can immediately be used!
		// - HTTP stuff SHOULD work after changing the hosts file, correctly using the
		// new DNS resolution - but if not, we will need to shut down the EXE and start
		// it back up.
	}

	private static void createButton(JPanel parent, String title, String text, ActionListener e) {
		JButton button = new JButton("<html><p style=\"font-weight:bold\">" + title + "</p><p>" + text + "</p></html>");
		button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
		button.addActionListener(e);
		parent.add(button);
	}

	private void addButton(JPanel parent, Mode mode, String title, String text) {
		createButton(parent, title, text, evt -> _onSelected.accept(mode));
	}
}
