package net.floodlightcontroller.packet;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class RTSP extends BasePacket {

	public static final String NEWLINE = "\r\n";

	private boolean isRequest;
	private String version;
	private Map<String, String> headerParameters;
	private String messageBody;

	// Only for requests
	private String method;
	private String uri;

	// Only for responses
	private int responseCode;
	private String responsePhrase;

	@Override
	public byte[] serialize() {
		StringBuffer sb = new StringBuffer();

		// write first line
		if (isRequest) {
			sb.append(method);
			sb.append(" ");
			sb.append(uri);
			sb.append(" ");
			sb.append(version);
		} else {
			sb.append(version);
			sb.append(" ");
			sb.append(responseCode);
			sb.append(" ");
			sb.append(responsePhrase);
		}
		sb.append(NEWLINE);

		// write headers
		for (Entry<String, String> entry : headerParameters.entrySet()) {
			sb.append(entry.getKey());
			sb.append(": ");
			sb.append(entry.getValue());
			sb.append(NEWLINE);
		}

		// write message body if necessary
		if (messageBody != null && messageBody.length() > 0) {
			sb.append(NEWLINE);
			sb.append(messageBody);
		}

		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length)
			throws PacketParsingException {
		try {
			headerParameters = new HashMap<String, String>();
			String payloadString = new String(data, offset, length,
					StandardCharsets.UTF_8);
			if (!payloadString.contains("RTSP"))
				throw new PacketParsingException("Packet does not contain RTSP string");

			String[] lines = payloadString.split("\\r?\\n");
			if (!lines[0].contains("RTSP"))
				throw new PacketParsingException("Header does not contain RTSP string");

			boolean isFirstline = true;
			boolean isMessageBody = false;
			StringBuilder body = new StringBuilder();
			for (String line : lines) {
				if (isFirstline) {
					// parse first line
					String[] firstline = lines[0].split(" ");
					if (firstline[0].startsWith("RTSP")) {
						// Response
						isRequest = false;
						version = firstline[0];
						responseCode = Integer.valueOf(firstline[1]);
						line.substring(version.length() + 1 + firstline[1].length() + 1);
					} else if (firstline[2].startsWith("RTSP")) {
						// Request
						isRequest = true;
						method = firstline[0];
						uri = firstline[1];
						version = firstline[2];
					} else
						throw new PacketParsingException(
								"Unsupported RTSP message");
					isFirstline = false;
				} else if (isMessageBody) {
					// append the line to the body
					body.append(line);
					body.append("\r\n");
				} else {
					if (line.equals("")) {
						// This is the empty line between headers and body
						isMessageBody = true;
					} else if (line.contains(": ")) {
						// parse a header line
						String[] headerline = line.split(": ");
						headerParameters.put(headerline[0], headerline[1]);
					} else {
						// ignore this line
					}
				}
			}

			// Save the body in a variable
			if (body.length() > 2) {
				body.setLength(body.length() - 2);
				messageBody = body.toString();
			}

			return this;
		} catch (Exception e) {
			throw new PacketParsingException(e.getMessage());
		}
	}

	public boolean isRequest() {
		return isRequest;
	}

	public void setRequest(boolean isRequest) {
		this.isRequest = isRequest;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Map<String, String> getHeaderParameters() {
		return headerParameters;
	}

	public void setHeaderParameters(Map<String, String> headerParameters) {
		this.headerParameters = headerParameters;
	}

	public String getMessageBody() {
		return messageBody;
	}

	public void setMessageBody(String messageBody) {
		this.messageBody = messageBody;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public int getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	public String getResponsePhrase() {
		return responsePhrase;
	}

	public void setResponsePhrase(String responsePhrase) {
		this.responsePhrase = responsePhrase;
	}

	@Override
	public String toString() {
		return "RTSP ["
				+ ", version="
				+ version
				+ (isRequest ? ", REQUEST, method=" + method + ", uri=" + uri
						: ", RESPONSE, responseCode=" + responseCode
								+ ", responsePhrase=" + responsePhrase)
				+ ", headerParameters=" + headerParameters + ", messageBody="
				+ messageBody + "]";
	}

}
