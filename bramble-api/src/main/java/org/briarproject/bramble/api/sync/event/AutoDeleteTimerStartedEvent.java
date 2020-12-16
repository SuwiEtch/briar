package org.briarproject.bramble.api.sync.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a message's auto-delete timer is started.
 */
@Immutable
@NotNullByDefault
public class AutoDeleteTimerStartedEvent extends Event {

	private final MessageId messageId;
	private final long autoDeleteDeadline;

	public AutoDeleteTimerStartedEvent(MessageId messageId,
			long autoDeleteDeadline) {
		this.messageId = messageId;
		this.autoDeleteDeadline = autoDeleteDeadline;
	}

	public MessageId getMessageId() {
		return messageId;
	}

	public long getAutoDeleteDeadline() {
		return autoDeleteDeadline;
	}
}
