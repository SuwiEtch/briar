package org.briarproject.briar.conversation;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.conversation.ConversationAutoDeleteManager;
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
		ConversationAutoDeleteManager conversationAutoDeleteManager;
	}

	@Provides
	@Singleton
	ConversationManager getConversationManager(
			ConversationManagerImpl conversationManager) {
		return conversationManager;
	}

	@Provides
	@Singleton
	ConversationAutoDeleteManager provideConversationAutoDeleteManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ConversationAutoDeleteManagerImpl conversationAutoDeleteManager) {
		lifecycleManager
				.registerOpenDatabaseHook(conversationAutoDeleteManager);
		contactManager.registerContactHook(conversationAutoDeleteManager);
		// Don't need to register with the client versioning manager as this
		// client's groups aren't shared with contacts
		return conversationAutoDeleteManager;
	}
}
