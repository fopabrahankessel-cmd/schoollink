package com.google.firebase.messaging;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Test access to the Firebase Admin SDK's package-private parts (task 2.7): FCM answers built by
 * hand, and a multicast's messages as the JSON sent to FCM. Lives in the SDK's package for that.
 */
public final class FcmFakes {

	private FcmFakes() {
	}

	public static SendResponse delivered(String messageId) {
		return SendResponse.fromMessageId(messageId);
	}

	/** {@code message} stands for FCM's text, which may quote the token. */
	public static SendResponse refused(MessagingErrorCode code, String message) {
		return SendResponse.fromException(failure(code, message));
	}

	public static FirebaseMessagingException failure(MessagingErrorCode code, String message) {
		return FirebaseMessagingException.withMessagingErrorCode(new FirebaseException(ErrorCode.UNKNOWN, message, null),
				code);
	}

	public static BatchResponse batch(SendResponse... responses) {
		List<SendResponse> list = List.of(responses);
		return new BatchResponse() {

			@Override
			public List<SendResponse> getResponses() {
				return list;
			}

			@Override
			public int getSuccessCount() {
				return (int) list.stream().filter(SendResponse::isSuccessful).count();
			}

			@Override
			public int getFailureCount() {
				return list.size() - getSuccessCount();
			}

		};
	}

	/** One JSON document per token, as FCM receives them. */
	public static List<String> json(MulticastMessage multicast) {
		return multicast.getMessageList().stream().map(message -> {
			try {
				return GsonFactory.getDefaultInstance().toString(message);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}).toList();
	}

}
