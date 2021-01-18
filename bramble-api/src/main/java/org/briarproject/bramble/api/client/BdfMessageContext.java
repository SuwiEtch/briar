package org.briarproject.bramble.api.client;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@Immutable
@NotNullByDefault
public class BdfMessageContext {

	private final BdfDictionary dictionary;
	private final Collection<MessageId> dependencies;
	private final long autoDeleteTimer;

	public BdfMessageContext(BdfDictionary dictionary) {
		this(dictionary, emptyList(), NO_AUTO_DELETE_TIMER);
	}

	public BdfMessageContext(BdfDictionary dictionary,
			Collection<MessageId> dependencies) {
		this(dictionary, dependencies, NO_AUTO_DELETE_TIMER);
	}

	public BdfMessageContext(BdfDictionary dictionary,
			Collection<MessageId> dependencies, long autoDeleteTimer) {
		this.dictionary = dictionary;
		this.dependencies = dependencies;
		this.autoDeleteTimer = autoDeleteTimer;
	}

	public BdfDictionary getDictionary() {
		return dictionary;
	}

	public Collection<MessageId> getDependencies() {
		return dependencies;
	}

	public long getAutoDeleteTimer() {
		return autoDeleteTimer;
	}
}
