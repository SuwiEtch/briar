package org.briarproject.briar.conversation;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.conversation.AutoDeleteManager;
import org.briarproject.briar.api.conversation.ConversationManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ConversationModule {

	public static class EagerSingletons {
		@Inject
		ConversationManager conversationManager;
		@Inject
		AutoDeleteManager autoDeleteManager;
	}

	@Provides
	@Singleton
	ConversationManager getConversationManager(
			ConversationManagerImpl conversationManager) {
		return conversationManager;
	}

	@Provides
	@Singleton
	AutoDeleteManager provideAutoDeleteManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			AutoDeleteManagerImpl autoDeleteManager) {
		lifecycleManager.registerOpenDatabaseHook(autoDeleteManager);
		contactManager.registerContactHook(autoDeleteManager);
		// Don't need to register with the client versioning manager as this
		// client's groups aren't shared with contacts
		return autoDeleteManager;
	}
}
