package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MessageContext {

	private final Metadata metadata;
	private final Collection<MessageId> dependencies;
	private final long autoDeleteTimer;

	public MessageContext(Metadata metadata,
			Collection<MessageId> dependencies, long autoDeleteTimer) {
		this.metadata = metadata;
		this.dependencies = dependencies;
		this.autoDeleteTimer = autoDeleteTimer;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public Collection<MessageId> getDependencies() {
		return dependencies;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}
}
