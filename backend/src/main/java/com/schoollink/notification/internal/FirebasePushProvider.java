package com.schoollink.notification.internal;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.schoollink.notification.internal.SendResult.Rejected;
import com.schoollink.notification.internal.SendResult.Retry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a push to every phone of the user through Firebase Cloud Messaging (BR-21). One phone
 * receiving it is enough. Tokens FCM no longer accepts are deleted. Passing failures (FCM down,
 * quota) are retried by the dispatcher (BR-24); if no phone can receive it, it fails at once and
 * the guardian is left to the SMS digest (BR-22).
 *
 * <p>NFR-9: tokens and FCM's error messages (which may quote a token) are never logged or stored;
 * only FCM's error codes are.
 */
class FirebasePushProvider implements PushProvider {

	private static final Logger log = LoggerFactory.getLogger(FirebasePushProvider.class);

	/** The app is gone from that phone: the token will never work again. */
	private static final Set<MessagingErrorCode> DEAD_TOKEN = Set.of(MessagingErrorCode.UNREGISTERED,
			MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH);

	/** Worth trying again later. */
	private static final Set<MessagingErrorCode> PASSING = Set.of(MessagingErrorCode.UNAVAILABLE,
			MessagingErrorCode.INTERNAL, MessagingErrorCode.QUOTA_EXCEEDED);

	private final FirebaseMessaging messaging;

	private final DeviceTokens devices;

	FirebasePushProvider(FirebaseMessaging messaging, DeviceTokens devices) {
		this.messaging = messaging;
		this.devices = devices;
	}

	@Override
	public SendResult send(PushMessage message) {
		List<String> tokens = devices.tokensOf(message.userId());
		if (tokens.isEmpty()) {
			return new Rejected("no device registered");
		}
		MulticastMessage multicast = MulticastMessage.builder()
			.addAllTokens(tokens)
			.setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
			// the app opens the notification in its inbox and reports it opened (BR-24, task 2.10)
			.putData("notificationId", message.notificationId().toString())
			// delivered at once, even when the phone is idle (BR-21)
			.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
			.build();
		BatchResponse batch;
		try {
			batch = messaging.sendEachForMulticast(multicast);
		}
		catch (FirebaseMessagingException ex) {
			// the whole request failed: no network, FCM down, or bad credentials
			log.warn("Push {}: FCM request failed with {}", message.notificationId(), code(ex));
			return new Retry("FCM " + code(ex));
		}
		List<String> dead = new ArrayList<>();
		Set<String> passing = new TreeSet<>();
		Set<String> refused = new TreeSet<>();
		boolean delivered = false;
		for (int i = 0; i < tokens.size(); i++) {
			SendResponse response = batch.getResponses().get(i);
			if (response.isSuccessful()) {
				delivered = true;
				continue;
			}
			MessagingErrorCode code = response.getException().getMessagingErrorCode();
			if (DEAD_TOKEN.contains(code)) {
				dead.add(tokens.get(i));
				refused.add(code.name());
			}
			else if (code == null || PASSING.contains(code)) {
				passing.add(code(response.getException()));
			}
			else {
				refused.add(code.name());
			}
		}
		devices.forget(dead);
		if (!dead.isEmpty()) {
			log.info("Push {}: {} device token(s) no longer valid, deleted", message.notificationId(), dead.size());
		}
		if (delivered) {
			return SendResult.sent();
		}
		if (!passing.isEmpty()) {
			return new Retry("FCM " + String.join(", ", passing));
		}
		return new Rejected("no device accepted it: " + String.join(", ", refused));
	}

	private static String code(FirebaseMessagingException ex) {
		return ex.getMessagingErrorCode() != null ? ex.getMessagingErrorCode().name() : String.valueOf(ex.getErrorCode());
	}

}
