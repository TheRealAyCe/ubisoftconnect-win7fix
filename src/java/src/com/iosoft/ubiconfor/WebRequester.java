package com.iosoft.ubiconfor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.iosoft.helpers.MiscIO;
import com.iosoft.ubiconfor.dtos.HeaderDto;
import com.iosoft.ubiconfor.dtos.RequestDto;
import com.iosoft.ubiconfor.dtos.ResponseDto;

public final class WebRequester {
	private WebRequester() {
	}

	public static void getResponse(RequestDto request, ResponseDto response) throws IOException {
		// TODO: If we get a cert error, the hosts file was modified before we got
		// started! communicate that?

		HttpURLConnection connection = (HttpURLConnection) new URL(request.Data.Uri).openConnection();
		// HttpURLConnection connection = (HttpURLConnection) new
		// URL("http://localhost").openConnection();
		connection.setUseCaches(false);

		connection.setRequestProperty("Accept", "*/*");
		connection.setRequestMethod(request.Data.Method);
		for (HeaderDto header : request.Data.Headers) {
			connection.setRequestProperty(header.Name, String.join(",", header.Values));
		}

		if (!request.Data.Method.equals("GET")) {
			connection.setDoOutput(true);
			try (OutputStream out = connection.getOutputStream()) {
				out.write(request.Data.Content);
				out.flush();
			}
		}

		System.out.println("IS: " + connection);

		connection.connect();

		try (InputStream in = connection.getInputStream()) {
			response.Data.Content = MiscIO.readFully(in);
		} catch (IOException e) {
			InputStream in = connection.getErrorStream();
			if (in != null) {
				try (InputStream in2 = in) {
					response.Data.Content = MiscIO.readFully(in2);
				}
			}
		}

		System.out.println("Got " + new String(response.Data.Content, StandardCharsets.UTF_8));

		response.Data.StatusCode = connection.getResponseCode();
		response.Data.Headers = connection.getHeaderFields().entrySet().stream() //
				.filter(x -> x.getKey() != null).<HeaderDto>map(x -> {
					HeaderDto header = new HeaderDto();
					header.Name = x.getKey();
					header.Values = x.getValue().toArray(new String[x.getValue().size()]);
					return header;
				}).toArray(HeaderDto[]::new);
	}
}
