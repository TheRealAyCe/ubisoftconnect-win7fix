# ubisoftconnect-win7fix
A proxy application to fix Ubisoft's `channel-service` not supporting any cipher suites for Windows 7, so that chat and multiplayer in Ubisoft Connect works again. Without it, Ubisoft Connect/Uplay may, on Windows 7, not show any chats, you cannot create groups, you cannot invite friends, and so on.

_Use at your own risk, of course, as always._

## [>> Download <<](https://github.com/TheRealAyCe/ubisoftconnect-win7fix/releases)
**You need Java 8 installed for this.**

### Installation
1. Make sure Java 8 is installed.
2. Extract the ZIP in some directory. You should have `ubisoftconnect-win7fix.exe` and a folder `Webserver`.

### Usage
1. Before starting Ubisoft Connect, run `ubisoftconnect-win7fix.exe` from the extracted folder. It needs admin permissions.
2. Allow the root certificate to be installed. It will be uninstalled (another prompt) when you close the app again.
3. Wait for the app to show "Ready!".
4. Start Ubisoft Connect.
5. Keep the app window open until you're done using Ubisoft Connect.

It will setup everything automatically and clean up after itself once you close it. It will ask for admin permissions due to having to change the `hosts` file and possibly because it's installing (and later removing again) a root certificate for your current Windows user.

## The problem
Ubisoft Connect loses chat and multiplayer party invite functionality under Windows 7, because the backend service that the application is trying to connect with does not support the HTTPS functionality shipped in Windows 7. The `launcher_log.txt` will contain lines with `Http status code is none  for url https://channel-service.upc.ubi.com/...`. Using Internet Explorer, which uses the same API to access HTTPS functionality as Ubisoft Connect, it is not possible to open `https://channel-service.upc.ubi.com/`.

And the reason why the Ubisoft service does not support Windows 7, is due to only allowing a small amount of cipher suites, out of which Windows 7 supports none.
From https://www.ssllabs.com/ssltest/analyze.html?d=channel-service.upc.ubi.com&s=54.147.167.217:
> TLS 1.2 (suites in server-preferred order):
> 
> TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 (0xc02f)   ECDH x25519 (eq. 3072 bits RSA)   FS 	128
> TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 (0xc030)   ECDH x25519 (eq. 3072 bits RSA)   FS 	256
> TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256 (0xcca8)   ECDH x25519 (eq. 3072 bits RSA)   FS 	256

List of supported cipher suites in Windows 7: https://docs.microsoft.com/en-us/windows/win32/secauthn/tls-cipher-suites-in-windows-7

A cipher suite determines how data is encrypted. So even though configuring TLS 1.2 may be a challenge of its own ([it's actually pretty simple](https://support.microsoft.com/en-us/topic/update-to-enable-tls-1-1-and-tls-1-2-as-default-secure-protocols-in-winhttp-in-windows-c4bd73d2-31d7-761e-0178-11268bb10392)) merely enabling TLS 1.2 support on Windows 7 does NOT solve the problem, [contrary to what the Ubisoft support may or may not have claimed](https://discussions.ubisoft.com/topic/108296/ubisoft-support-are-playing-games-with-me-i-need-to-make-a-complaint-case-15170691/15?lang=en-US). TLS 1.2 is working correctly, but the way traffic gets encrypted by the server is not understood by Windows 7.

## The solution
The proper solution would be for Ubisoft to allow at least one cipher suite that is still supported in Windows 7 for communication with their server. [Windows 7 is still officially claimed to be supported](https://www.ubisoft.com/en-gb/help/gameplay/article/system-requirements-for-anno-1602-history-edition/000081194), so in my opinion there is no reason why Ubisoft should drop support in this way.

## The workaround
My workaround "solution", is to spoof their endpoint server, and redirect the traffic to their server using an implementation that is independent of the operating system's HTTPS cipher suites.

This is what I did/the ubisoftconnect-win7fix does:

1. Create a self-signed certificate for the hostname `channel-service.upc.ubi.com`. This is needed so that we can pretend to be the Ubisoft endpoint that causes problems.
2. Install it as a root certificate on the machine using Ubisoft Connect. This is needed so that Ubisoft Connect will accept our proxy server as the real deal.
3. Create a webserver app with ASP.NET Core, using the self-signed root certificate, to act as our proxy server. This app will receive HTTPS requests from Ubisoft Connect, negotiating a cipher suite that is supported in Windows 7, and then pipe the request to the actual Ubisoft endpoint.
4. The actual Ubisoft endpoint can be contacted either by another machine in the network running Windows 10 (so the webserver app would run on that and directly contact the Ubisoft endpoint, giving back the actual HTTP response), or by using another app written in a separate framework, like Java, which does not use the operating system's libraries for HTTPS communication.
5. Adapt the `hosts` file, adding `127.0.0.1  channel-service.upc.ubi.com`, so that any requests to the Ubisoft endpoint are redirected to our webserver app instead. If we use a Java application running on the same machine, this must be the localhost address, if you use another machine in your network it must be that machine's network IP address.
6. You can test if you set up everything correctly by using Internet Explorer to access the URL.

## Related links
- https://discussions.ubisoft.com/topic/107880/cannot-read-send-chat-messages-tls-1-2-not-supported/2?lang=en-US
- https://www.ubisoft.com/en-us/help/connectivity-and-performance/article/missing-chat-messages-and-game-invitations-in-ubisoft-connect/000097837
- https://discussions.ubisoft.com/topic/108296/ubisoft-support-are-playing-games-with-me-i-need-to-make-a-complaint-case-15170691/15?lang=en-US
- https://superuser.com/questions/1687537/forcing-tls-1-2-on-windows-7
- https://discussions.ubisoft.com/topic/121483/fixed-chats-groups-invites-not-working-in-windows-7?lang=en-US
