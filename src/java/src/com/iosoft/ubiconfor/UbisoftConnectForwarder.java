package com.iosoft.ubiconfor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import com.iosoft.helpers.Mutable;
import com.iosoft.helpers.MutableBool;
import com.iosoft.helpers.MutableInt;
import com.iosoft.helpers.WeirdException;
import com.iosoft.helpers.async.Async;
import com.iosoft.helpers.async.Task;
import com.iosoft.helpers.async.TaskSource;
import com.iosoft.helpers.async.VTask;
import com.iosoft.helpers.async.dispatcher.Dispatcher;
import com.iosoft.helpers.async.dispatcher.EDTDispatcher;
import com.iosoft.helpers.awt.MiscImg;
import com.iosoft.helpers.network.tcp.TcpConnecter;
import com.iosoft.helpers.network.tcp.TcpListener;
import com.iosoft.helpers.ui.awt.ErrorScreen;
import com.iosoft.helpers.ui.awt.GameTextPane;
import com.iosoft.helpers.ui.awt.MiscAWT;
import com.iosoft.helpers.web.MiscWeb;
import com.iosoft.ubiconfor.dtos.J_Code;
import com.iosoft.ubiconfor.dtos.J_Hello;
import com.iosoft.ubiconfor.dtos.J_ResponseInitialDto;
import com.iosoft.ubiconfor.dtos.J_ResponseProgressDto;
import com.iosoft.ubiconfor.dtos.WS_AbortRequest;
import com.iosoft.ubiconfor.dtos.WS_RequestDto;
import com.iosoft.ubiconfor.dtos.WS_WebserverErrorDto;
import com.iosoft.ubiconfor.dtos.WS_WebserverReady;
import com.iosoft.ubiconfor.dtos.WS_WebserverReady.Ready;

public final class UbisoftConnectForwarder {
	private static final String[] Domains = { //
			/*"remote-config.gog.com", //
			"content-system.gog.com", //
			"www.gog.com", //
			"api.gog.com", //
			"reviews.gog.com", //
			"external-users.gog.com", //
			"gameplay.gog.com", //
			"chat.gog.com", //
			"catalog.gog.com", //
			"recommendations-api.gog.com", //
			"cloudstorage.gog.com", //
			"gamesdb.gog.com", //
			"cfg.gog.com", //
			"auth.gog.com", //
*/
			//"store-static-modular.gog-statics.com", //
			//"images.gog-statics.com", //
			
			"channel-service.upc.ubi.com",
			
	};

	public static void main(String[] args) throws Exception {
		// keep the cache forever, so that we can change the hosts file without a
		// problem
		java.security.Security.setProperty("networkaddress.cache.ttl", "-1");

		String exePath;
		if (Arrays.asList(args).contains("-noexe")) {
			exePath = null;
		} else {
			exePath = args.length == 0 ? "Webserver" : args[0];
		}
		EDTDispatcher.initialize().dispatch(() -> new UbisoftConnectForwarder(exePath));
	}

	private final String _exePath; // null=use default, ""=none, other=that path
	private Map<Integer, VTask> _pendingRequests = new HashMap<>();
	private final TcpListener _tcpListener;

	private int _numRequests, _numRequestsCompleted, _numRequestsFailed;
	private Connection _webserverConnection;

	private Mode _mode;

	private final SelectModeScreen _selectModeScreen;
	private final JFrame _window;
	private final JPanel _panel;
	private final GameTextPane _labelCurrentStatus;
	private final JLabel _labelRequests;
	private final JPanel _bottomPanel;
	private final RequestsView _requestsView;

	private UbisoftConnectForwarder(String exePath) {
		_exePath = exePath;

		MiscAWT.setSystemLookAndFeel();

		_window = new JFrame("ubisoftconnect-win7fix by AyCe");
		_window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		try {
			_window.setIconImage(MiscImg.loadImage("/Assets/icon.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		_labelCurrentStatus = new GameTextPane("Starting...");
		_labelCurrentStatus.setFont(_labelCurrentStatus.getFont().deriveFont(20f));
		_labelCurrentStatus.setHorizontalCentered();
		_panel = new JPanel(new BorderLayout());
		_panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JLabel labelVersion = new JLabel("v4 - 2026-02-15");
		labelVersion.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		_panel.add(labelVersion, BorderLayout.NORTH);

		_bottomPanel = new JPanel(new BorderLayout());
		_bottomPanel.setOpaque(false);
		_labelRequests = new JLabel();
		_labelRequests.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
		_bottomPanel.add(_labelRequests, BorderLayout.CENTER);
		JButton buttonShowRequests = new JButton("Show");
		buttonShowRequests.setOpaque(false);
		_bottomPanel.add(buttonShowRequests, BorderLayout.WEST);

		// 1. Allow selection of operation mode
		_selectModeScreen = new SelectModeScreen(this::selectMode, _exePath);
		_panel.add(_selectModeScreen.SelectionDialog, BorderLayout.CENTER);
		_window.add(_panel);

		_window.setPreferredSize(new Dimension(600, 450));
		_window.setMinimumSize(new Dimension(600, 450));
		_window.pack();
		_window.setLocationRelativeTo(null);
		_window.setVisible(true);

		JFrame requestsWindow = new JFrame("Requests");
		requestsWindow.setIconImage(_window.getIconImage());
		requestsWindow.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		_requestsView = new RequestsView();
		requestsWindow.add(_requestsView.Panel);
		requestsWindow.setPreferredSize(new Dimension(400, 300));
		requestsWindow.setMinimumSize(new Dimension(400, 300));
		requestsWindow.pack();
		requestsWindow.setLocationRelativeTo(_window);

		buttonShowRequests.addActionListener(evt -> requestsWindow.setVisible(true));

		Dispatcher.getForCurrentThread()
				.setMainUnhandledExceptionHandler(x -> ErrorScreen.showAndDump(_window, x, "ubisoftconnect-win7fix"));

		_tcpListener = new TcpListener(this::onWebserverConnected);
	}

	private void selectMode(Mode mode) {
		if (_mode != null) {
			// wtf
			return;
		}

		_mode = mode;
		_panel.remove(_selectModeScreen.SelectionDialog);
		// 2. Show options for the selected mode
		// if(_mode == )

		_panel.add(_labelCurrentStatus, BorderLayout.CENTER);

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
			if (_exePath == null) {
				// don't start the webserver
				return;
			}

			File exeFolder = new File(_exePath);
			String exeName = "UbisoftConnectProxy.exe";
			File exeFile = new File(exeFolder, exeName);

			if (!exeFile.exists()) {
				throw new IOException("Webserver EXE not found: '" + exeFile.getAbsolutePath() + "'");
			}

			// make sure it's not currently running
			new ProcessBuilder("taskkill", "/IM", exeName).inheritIO().start();
			// Runtime.getRuntime().exec("taskkill /IM " + exeName);
			Misc.sleep(500);
			Process process = new ProcessBuilder( //
					// "cmd.exe", //
					// "/C", //
					exeFile.getAbsolutePath(), //
					Integer.toString(port)) //
					.directory(exeFolder) //
					.inheritIO() //
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

	private void onWebserverReady(WS_WebserverReady msg) {
		if (msg.What == Ready.Hosts) {
			setStatus("Creating DNS cache entry...", null);
			// 4. Hosts file ready (no redirect) -> make "warmup" request so that we
			// remember the actual IP address
			Async.runAsyncWrap(() -> {
				// ensure SSL classes are loaded without interruption (probably not needed here)
				MiscWeb.ensureSSLWarmupIsDone();
				// SSLUtilities.trustAllHttpsCertificates();
				// make sure the DNS lookup is done
				// MiscWeb.getFirstLine("https://channel-service.upc.ubi.com/");
				for (String domain : Domains) {
					MiscWeb.getFirstLine("https://" + domain + "/");
				}
			}).await(error -> {
				if (error != null) {
					setError("Error while warming up: " + error);
					error.printStackTrace();
				} else if (_webserverConnection != null) {
					setStatus("Installing certificate...", null);
					_webserverConnection.sendStatus(J_Code.Code_DnsReady);
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
		@SuppressWarnings("resource")
		TcpListener tcpListener = new TcpListener((Socket x) -> mutHandler.Value.accept(x));
		try {
			tcpListener.start((char) 443);
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
			if (clientError != null && taskServer.isRunning()) {
				// we can abort that
				taskAcceptTimeout.cancel();
				tsServerConnected.setResult(null);
			}
		});

		// can we connect to it? (executed right after server was started successfully)
		Task<TcpConnecter.Result> taskConnect = TcpConnecter.connectAsync("127.0.0.1", (char) 443);
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
			if (serverError != null && taskClient.isRunning()) {
				// we can abort that
				taskConnectTimeout.cancel();
				tsClientConnected.setResult(null);
			}
		});

		return taskSource.getTask();
	}

	private char startTcpServerGetPort() throws IOException {
		try {
			_tcpListener.start((char) 44449);
		} catch (IOException e) {
			// port blocked
			try {
				_tcpListener.start((char) 0);
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
					this::onAbortRequest, this::onWebserverReady, this::onWebserverError);

			// only one client pls
			_tcpListener.stop();

			setStatus("Webserver connected, checking hosts and certificates...", null);

			J_Hello msg = new J_Hello();
			msg.Domains = Domains;
			_webserverConnection.send(msg);
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
		for (VTask pendingRequest : _pendingRequests.values()) {
			pendingRequest.cancel();
		}
		_pendingRequests.clear();
		setError("Webserver lost the connection, please restart the app.");
	}

	private void onWebserverError(WS_WebserverErrorDto dto) {
		if (dto.Fatal) {
			_webserverConnection.kick();
		}
		setError("Webserver error" + (dto.Fatal ? " (fatal)" : "") + ": " + dto.Text);
	}

	private void onAbortRequest(WS_AbortRequest msg) {
		VTask task = _pendingRequests.get(msg.RequestId);
		if (task == null) {
			System.out.println("Aborting request " + msg.RequestId + " failed, not found.");
			return;
		}

		System.out.println("Aborting request " + msg.RequestId + "...");
		_pendingRequests.remove(msg.RequestId, task);
		task.cancel();
	}

	private void onRequest(WS_RequestDto request) {
		System.out
				.println("Got a request: " + request.RequestId + " -> " + request.Data.Method + " " + request.Data.Uri);

		J_ResponseInitialDto initial = new J_ResponseInitialDto();
		initial.RequestId = request.RequestId;

		if (_pendingRequests.size() > 20) {
			// most likely a self-referring loop. bad!
			// TODO: Communicate better?
			setError("Requests seem to get redirected back to localhost?!");
			_webserverConnection.send(initial);
			return;
		}

		Mutable<VTask> mutTask = new Mutable<>(null);
		MutableBool initialSent = new MutableBool();
		MutableBool finished = new MutableBool();
		MutableInt totalContentLength = new MutableInt();
		Dispatcher dispatcher = Dispatcher.getForCurrentThread();
		mutTask.Value = Async.runAsyncWrap(() -> {
			WebRequest webRequest = new WebRequest(dto -> {
				//System.out.println("Got update for request " + request.RequestId + " -> (" + dto.Response.Payload.length
				//		+ " bytes read, finished=" + dto.Finish + ")");
				dispatcher.dispatch(() -> {
					if (_webserverConnection != null) {
						if (finished.Value) {
							return;
						}
						if (!initialSent.Value) {
							throw new IllegalStateException("Update before initial data?!");
						}
						if (dto.Finish) {
							finished.Value = true;
						}
						totalContentLength.Value += dto.Response.Payload.length;
						J_ResponseProgressDto msg = new J_ResponseProgressDto();
						msg.RequestId = request.RequestId;
						msg.Data = dto;
						_webserverConnection.send(msg);
					}
				});
			});
			webRequest.getResponse(request, dto -> {
				System.out.println("Got initial data for request " + request.RequestId + " -> (" + dto.StatusCode + ", "
						+ dto.Headers.Headers.length + " headers)");
				dispatcher.dispatch(() -> {
					if (_webserverConnection != null) {
						if (initialSent.Value) {
							return;
						}
						initialSent.Value = true;
						initial.Data = dto;
						_webserverConnection.send(initial);
					}
				});
			});
		}).awaitAndContinue(ex -> {
			_numRequestsCompleted++;
			if (ex != null) {
				_numRequestsFailed++;
				System.out.println("Error with request '" + request.RequestId + "': " + ex);
			}
			_requestsView.add(request, initial.Data, totalContentLength.Value, ex);
			updateRequestsLabel();

			_pendingRequests.remove(request.RequestId, mutTask.Value);
			if (!initialSent.Value) {
				initialSent.Value = true;
				if (_webserverConnection != null) {
					_webserverConnection.send(initial);
				}
			}
			if (!finished.Value) {
				finished.Value = true;
				if (_webserverConnection != null) {
					J_ResponseProgressDto msg = new J_ResponseProgressDto();
					msg.RequestId = request.RequestId;
					msg.Data.Finish = true;
					_webserverConnection.send(msg);
				}
			}
		});
		_pendingRequests.put(request.RequestId, mutTask.Value);
		_numRequests++;
		updateRequestsLabel();
	}

	private void updateRequestsLabel() {
		_labelRequests.setText("" + _numRequests + " requests, " + _numRequestsCompleted + " completed, "
				+ _numRequestsFailed + " failed");
	}
}
