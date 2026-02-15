package com.iosoft.ubiconfor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;

import com.iosoft.helpers.Misc;
import com.iosoft.helpers.MiscLINQ;
import com.iosoft.helpers.io.MiscIO;
import com.iosoft.ubiconfor.dtos.BytesDto;
import com.iosoft.ubiconfor.dtos.HeaderDto;
import com.iosoft.ubiconfor.dtos.HeadersDto;
import com.iosoft.ubiconfor.dtos.ResponseHeaderDto;
import com.iosoft.ubiconfor.dtos.ResponseProgressDto;
import com.iosoft.ubiconfor.dtos.WS_RequestDto;

public final class WebRequest {
	private static final HashSet<String> SafeContentTypes = new HashSet<>();
	static {
		SafeContentTypes.add("text/plain");
		SafeContentTypes.add("text/html");
		SafeContentTypes.add("text/css");
		SafeContentTypes.add("text/csv");
		SafeContentTypes.add("application/json");
	}

	private final Consumer<ResponseProgressDto> _onProgress;

	private byte[] _firstBatch;

	public WebRequest(Consumer<ResponseProgressDto> onProgress) {
		_onProgress = onProgress;
	}

	public void getResponse(WS_RequestDto request, Consumer<ResponseHeaderDto> onHeaders) throws Exception {
		// Throws 'Exception' because not just an IOException can occur in here, and we
		// don't really care either way.

		// TODO: If we get a cert error, the hosts file was modified before we got
		// started! communicate that?

		HttpURLConnection connection = (HttpURLConnection) new URL(request.Data.Uri).openConnection();
		try {
			// HttpURLConnection connection = (HttpURLConnection) new
			// URL("http://localhost").openConnection();
			connection.setUseCaches(false);

			connection.setRequestProperty("Accept", "*/*");
			connection.setRequestMethod(request.Data.Method);
			for (HeaderDto header : request.Data.Headers.Headers) {
				// this could throw IllegalArgumentException (other 'set's probably too!)
				connection.setRequestProperty(header.Name, String.join(",", header.Values));
			}

			if (!request.Data.Method.equals("GET")) {
				connection.setDoOutput(true);
				try (OutputStream out = connection.getOutputStream()) {
					out.write(request.Data.Content.Payload);
					out.flush();
				}
			}

			System.out.println("Connecting to: " + connection);

			connection.connect();

			ResponseHeaderDto initial = new ResponseHeaderDto();

			initial.StatusCode = connection.getResponseCode();
			initial.Headers = new HeadersDto();
			initial.Headers.Headers = connection.getHeaderFields().entrySet().stream() //
					.filter(x -> x.getKey() != null).<HeaderDto>map(x -> {
						HeaderDto header = new HeaderDto();
						header.Name = x.getKey();
						header.Values = x.getValue().toArray(new String[x.getValue().size()]);
						return header;
					}).toArray(HeaderDto[]::new);

			onHeaders.accept(initial);

			try (InputStream in = connection.getInputStream()) {
				readBody(in);
			} catch (IOException e) {
				InputStream in = connection.getErrorStream();
				if (in != null) {
					try (InputStream in2 = in) {
						readBody(in2);
					}
				}
			}

			// System.out.println("Got " + new String(response.Data.Content,
			// StandardCharsets.UTF_8));
			System.out.println("Finished reading " + request.Data.Uri);
			HeaderDto contentTypeHeader = MiscLINQ.firstOrDefault(initial.Headers.Headers,
					x -> Misc.toUpper(x.Name).equals("CONTENT-TYPE"));
			if (_firstBatch != null && contentTypeHeader != null && MiscLINQ.any(contentTypeHeader.Values,
					x -> SafeContentTypes.contains(x) || SafeContentTypes.stream().anyMatch(y -> x.startsWith(y)))) {
				System.out.println("Body: " + new String(_firstBatch, StandardCharsets.UTF_8));
			}
		} finally {
			try {
				connection.disconnect();
			} catch (Exception e) {
				System.out.println("Error disconnecting: " + e);
			}
		}
	}

	private void readBody(InputStream in) throws IOException {
		int available = in.available();
		// if (available > 0) {
		// System.out.println("info: available = " + available + " for " +
		// getClassName(in));
		// }
		// If more is available than the buffer size, great! We need to read everything
		// anyways!
		int bufferSize = Math.max(available, 1024*1024);

		byte[] buffer = new byte[bufferSize];

		while (true) {
			if (Thread.currentThread().isInterrupted()) {
				System.out.println("Request aborted, stop reading body...");
				return;
			}
			ResponseProgressDto dto = new ResponseProgressDto();

			int read = in.read(buffer, 0, bufferSize);
			if (read == -1) {
				// done
				dto.Response.Payload = Misc.EMPTYBYTES;
				dto.Finish = true;
				_onProgress.accept(dto);
				return;
			}

			byte[] data = Arrays.copyOf(buffer, read);
			if (_firstBatch == null) {
				_firstBatch = data;
			}

			dto.Response.Payload = data;
			_onProgress.accept(dto);
		}
	}
}
