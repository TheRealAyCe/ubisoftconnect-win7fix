package com.iosoft.ubiconfor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.text.StyleConstants;

import com.iosoft.helpers.Misc;
import com.iosoft.helpers.MiscImg;
import com.iosoft.helpers.Mutable;
import com.iosoft.helpers.WeirdException;
import com.iosoft.helpers.async.Async;
import com.iosoft.helpers.async.Task;
import com.iosoft.helpers.async.TaskSource;
import com.iosoft.helpers.async.VTask;
import com.iosoft.helpers.async.dispatcher.Dispatcher;
import com.iosoft.helpers.async.dispatcher.EDTDispatcher;
import com.iosoft.helpers.network.tcp.TcpConnecter;
import com.iosoft.helpers.network.tcp.TcpListener;
import com.iosoft.helpers.ui.awt.ErrorScreen;
import com.iosoft.helpers.ui.awt.GameTextPane;
import com.iosoft.helpers.ui.awt.MiscAWT;
import com.iosoft.helpers.web.MiscWeb;
import com.iosoft.ubiconfor.dtos.HeaderDto;
import com.iosoft.ubiconfor.dtos.RequestDto;
import com.iosoft.ubiconfor.dtos.ResponseDataDto;
import com.iosoft.ubiconfor.dtos.ResponseDto;
import com.iosoft.ubiconfor.dtos.WebserverErrorDto;
import com.iosoft.ubiconfor.dtos.WebserverReady;
import com.iosoft.ubiconfor.dtos.WebserverReady.Ready;

public final class UbisoftConnectForwarder {
	public static void main(String[] args) throws Exception {
		// keep the cache forever, so that we can change the hosts file without a
		// problem
		java.security.Security.setProperty("networkaddress.cache.ttl", "-1");

		String exePath;
		if (Arrays.asList(args).contains("-noexe")) {
			exePath = "";
		} else {
			exePath = args.length == 0 ? null : args[0];
		}
		EDTDispatcher.initialize().dispatch(() -> new UbisoftConnectForwarder(exePath));
	}

	private final String _exePath; // null=use default, ""=none, other=that path
	private final List<VTask> _pendingRequests = new ArrayList<>();
	private final TcpListener _tcpListener;

	private int _numRequests, _numRequestsCompleted, _numRequestsFailed;
	private Connection _webserverConnection;

	private final JPanel _panel;
	private final GameTextPane _labelCurrentStatus;
	private final JLabel _labelRequests;
	private final JPanel _bottomPanel;
	private final RequestsView _requestsView;

	private UbisoftConnectForwarder(String exePath) {
		_exePath = exePath;

		MiscAWT.setSystemLookAndFeel();

		JFrame window = new JFrame("ubisoftconnect-win7fix by AyCe");
		window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		try {
			window.setIconImage(MiscImg.loadImage("/Assets/icon.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		_labelCurrentStatus = new GameTextPane("Starting...");
		_labelCurrentStatus.setFont(_labelCurrentStatus.getFont().deriveFont(20f));
		_labelCurrentStatus.setHorizontalCentered();
		_panel = new JPanel(new BorderLayout());
		_panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		_panel.add(new JLabel("v2pre1 - 2022-01-18"), BorderLayout.NORTH);
		_panel.add(_labelCurrentStatus, BorderLayout.CENTER);
		
		_bottomPanel = new JPanel(new BorderLayout());
		_bottomPanel.setOpaque(false);
		_labelRequests = new JLabel();
		_labelRequests.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
		_bottomPanel.add(_labelRequests, BorderLayout.CENTER);
		JButton buttonShowRequests = new JButton("Show");
		buttonShowRequests.setOpaque(false);
		_bottomPanel.add(buttonShowRequests, BorderLayout.WEST);
		
		window.add(_panel);
		window.setPreferredSize(new Dimension(400, 300));
		window.setMinimumSize(new Dimension(400, 300));
		window.pack();
		window.setLocationRelativeTo(null);
		window.setVisible(true);

		JFrame requestsWindow = new JFrame("Requests");
		requestsWindow.setIconImage(window.getIconImage());
		requestsWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		_requestsView = new RequestsView();
		requestsWindow.add(_requestsView.Panel);
		requestsWindow.setPreferredSize(new Dimension(400, 300));
		requestsWindow.setMinimumSize(new Dimension(400, 300));
		requestsWindow.pack();
		requestsWindow.setLocationRelativeTo(window);

		buttonShowRequests.addActionListener(evt -> requestsWindow.setVisible(true));

		Dispatcher.getForCurrentThread()
				.setMainUnhandledExceptionHandler(x -> ErrorScreen.showAndDump(window, x, "ubisoftconnect-win7fix"));

		_tcpListener = new TcpListener(this::onWebserverConnected);

		// 1. Check if port 443 is available
		startTcp443();
	}

	private void setStatus(String text, Boolean isGood) {
		_labelCurrentStatus.setText(text);
		_labelCurrentStatus.setForeground(isGood == null || isGood.booleanValue() ? Color.BLACK : Color.RED);
		// _labelCurrentStatus.addHyperlink("testuhu", null);
	}

	private void startTcp443() {
		check443Async().await(error -> {
			if (error == null) {
				setStatus("TCP 443 is available!", null);
				onTcp443Done();
			} else {
				_labelCurrentStatus.setAlign(StyleConstants.ALIGN_JUSTIFIED);
				setError(
						"TCP port 443 seems to be already in use by another application, likely a webserver. As we need to start our own webserver, you must temporarily close that application. Restart this tool afterwards to try again.\n\nWays to find out which application is responsible:\n- Visiting https://localhost and see where you land.\n- Use TCPView (download from Microsoft).\n\nError message:\n"
								+ error);
				error.printStackTrace();
			}
		});
	}

	private void onTcp443Done() {
		// 2. Start TCP server
		char tcpPort;
		try {
			tcpPort = startTcpServerGetPort();
			// will now wait for a connection
			setStatus("Launching webserver...", null);
			startExe(tcpPort);
		} catch (IOException e) {
			e.printStackTrace();
			setError(e.getMessage());
		}

	}

	private void startExe(char port) {
		// 3. Open the EXE
		Async.runAsyncWrap(() -> {
			if (_exePath != null && _exePath.isEmpty()) {
				// don't start the webserver
				return;
			}

			File exeFolder = new File(_exePath == null ? "Webserver" : _exePath);
			String exeName = "UbisoftConnectProxy.exe";
			File exeFile = new File(exeFolder, exeName);

			if (!exeFile.exists()) {
				throw new IOException("Webserver EXE not found: '" + exeFile.getAbsolutePath() + "'");
			}

			// make sure it's not currently running
			Runtime.getRuntime().exec("taskkill /IM " + exeName);
			Misc.sleep(500);
			Process process = new ProcessBuilder( //
					// "cmd.exe", //
					// "/C", //
					exeFile.getAbsolutePath(), //
					Integer.toString(port)) //
							.directory(exeFolder) //
							.start();
			// don't kill, it should kill itself!
			// Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
		}).await(error -> {
			if (error != null) {
				setError("Could not start webserver: " + error);
				error.printStackTrace();
			}
		});
	}

	private void onWebserverReady(WebserverReady msg) {
		if (msg.What == Ready.Hosts) {
			setStatus("Creating DNS cache entry...", null);
			// 4. Hosts file ready (no redirect) -> make "warmup" request so that we
			// remember the actual IP address
			Async.runAsyncWrap(() -> {
				// ensure SSL classes are loaded without interruption (probably not needed here)
				MiscWeb.ensureSSLWarmupIsDone();
				// SSLUtilities.trustAllHttpsCertificates();
				// make sure the DNS lookup is done
				MiscWeb.getFirstLine("https://channel-service.upc.ubi.com/");
			}).await(error -> {
				if (error != null) {
					setError("Error while warming up: " + error);
					error.printStackTrace();
				} else if (_webserverConnection != null) {
					setStatus("Installing certificate...", null);
					_webserverConnection.sendDnsReady();
				} else {
					setError("Webserver is not connected anymore!");
				}
			});
		} else if (msg.What == Ready.Starting) {
			setStatus("Starting web listener...", null);
		} else if (msg.What == Ready.Running) {
			_panel.setBackground(new Color(170, 255, 170));
			setStatus("Ready!\n\nYou can start Ubisoft Connect now.\n\nKeep this window open.", true);
			_panel.add(_bottomPanel, BorderLayout.SOUTH);
			updateRequestsLabel();
		}
	}

	private void setError(String text) {
		_labelCurrentStatus.setFont(_labelRequests.getFont());
		setStatus(text, false);
	}

	private Task<Exception> check443Async() {
		final TaskSource<Exception> taskSource = new TaskSource<>();

		setStatus("Checking TCP 443...", null);

		TaskSource<Exception> tsServerConnected = new TaskSource<>();
		TaskSource<Exception> tsClientConnected = new TaskSource<>();
		Task<Exception> taskServer = tsServerConnected.getTask();
		Task<Exception> taskClient = tsClientConnected.getTask();
		Runnable checkBothDone = () -> {
			if (taskServer.isCompleted() && taskClient.isCompleted()) {
				// done!
				Exception exception = taskServer.get();
				if (exception == null) {
					exception = taskClient.get();
				}
				taskSource.setResult(exception);
			}
		};
		taskClient.await(x -> checkBothDone.run());

		// can we accept?
		Mutable<Consumer<Socket>> mutHandler = new Mutable<>();
		TcpListener tcpListener = new TcpListener((Socket x) -> mutHandler.Value.accept(x));
		try {
			tcpListener.start(null, (char) 443, true);
		} catch (IOException e) {
			tcpListener.dispose();
			taskSource.setResult(e);
			return taskSource.getTask();
		}
		taskServer.await(x -> {
			tcpListener.dispose();
			checkBothDone.run();
		});
		VTask taskAcceptTimeout = VTask.delay(5);
		mutHandler.Value = socket -> {
			// only the first connection counts, close listener after that
			taskAcceptTimeout.cancel();
			Misc.forceClose(socket);
			tsServerConnected.setResult(null);
		};
		taskAcceptTimeout.await(() -> {
			tsServerConnected.setResult(new TimeoutException("No client connected"));
		});
		taskClient.await(clientError -> {
			if (clientError != null && tsServerConnected.getTask().isRunning()) {
				// we can abort that
				taskAcceptTimeout.cancel();
				tsServerConnected.setResult(null);
			}
		});

		// can we connect to it? (executed right after server was started successfully)
		Task<TcpConnecter.Result> taskConnect = TcpConnecter.connectAsync("127.0.0.1", (char) 443, true);
		// running, now wait for a client (max 5 sec)
		VTask taskConnectTimeout = VTask.delay(5);
		taskConnectTimeout.await(() -> {
			// close everything
			taskConnect.cancel();
			tsClientConnected.setResult(new TimeoutException("Could not connect to server"));
		});
		taskConnect.await(result -> {
			taskConnectTimeout.cancel();
			if (result.Value != null) {
				// disconnect
				Misc.forceClose(result.Value);
			}
			tsClientConnected.setResult(result.Exception);
		});
		taskServer.await(serverError -> {
			if (serverError != null && tsClientConnected.getTask().isRunning()) {
				// we can abort that
				taskConnectTimeout.cancel();
				tsClientConnected.setResult(null);
			}
		});

		return taskSource.getTask();
	}

	private char startTcpServerGetPort() throws IOException {
		try {
			_tcpListener.start(null, (char) 44449, true);
		} catch (IOException e) {
			// port blocked
			try {
				_tcpListener.start(null, (char) 0, true);
			} catch (IOException e1) {
				// could not start at all :(
				throw e1;
			}
		}

		setStatus("Waiting for webserver to connect via TCP...", null);

		return _tcpListener.getPort();
	}

	private void onWebserverConnected(Socket socket) {
		try {
			_webserverConnection = new Connection(socket, this::onWebserverDisconnected, this::onRequest,
					this::onWebserverReady, this::onWebserverError);

			// only one client pls
			_tcpListener.stop();

			setStatus("Webserver connected, checking hosts and certificates...", null);
			// waiting for hosts file...
		} catch (WeirdException e) {
			// too bad
			e.printStackTrace();
			setError("What? " + e);
		}
	}

	private void onWebserverDisconnected(Exception e) {
		_webserverConnection = null;
		System.out.println("Got disconnected: " + e);
		for (VTask pendingRequest : _pendingRequests) {
			pendingRequest.cancel();
		}
		_pendingRequests.clear();
		setError("Webserver lost the connection, please restart the app.");
	}

	private void onWebserverError(WebserverErrorDto dto) {
		if (dto.Fatal) {
			_webserverConnection.kick();
		}
		setError("Webserver error" + (dto.Fatal ? " (fatal)" : "") + ": " + dto.Text);
	}

	private void onRequest(RequestDto msg) {
		System.out.println("Got a request: " + msg.RequestId + " -> " + msg.Data.Method + " " + msg.Data.Uri);
		ResponseDto response = new ResponseDto();
		response.RequestId = msg.RequestId;
		response.Data = new ResponseDataDto();
		response.Data.StatusCode = 500;
		response.Data.Content = new byte[0];
		response.Data.Headers = new HeaderDto[0];

		if (_pendingRequests.size() > 20) {
			// most likely a self-referring loop. bad!
			// TODO: Communicate better?
			setError("Requests seem to get redirected back to localhost?!");
			_webserverConnection.send(response);
			return;
		}

		Mutable<VTask> mutTask = new Mutable<>(null);
		mutTask.Value = Async.runAsyncWrap(() -> WebRequester.getResponse(msg, response)).awaitAndContinue(ex -> {
			_numRequestsCompleted++;
			if (ex != null) {
				_numRequestsFailed++;
				System.out.println("Error with request '" + msg.RequestId + "': " + ex);
			}
			_requestsView.add(msg.Data, response.Data, ex);
			updateRequestsLabel();

			_pendingRequests.remove(mutTask.Value);
			if (_webserverConnection != null) {
				_webserverConnection.send(response);
			}
		});
		_pendingRequests.add(mutTask.Value);
		_numRequests++;
		updateRequestsLabel();
	}

	private void updateRequestsLabel() {
		_labelRequests.setText("" + _numRequests + " requests, " + _numRequestsCompleted + " completed, "
				+ _numRequestsFailed + " failed");
	}
}
