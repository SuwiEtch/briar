package org.briarproject.briar.android.contact;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public class ContactModule {

	@Module
	public interface BindsModule {
		@Binds
		@IntoMap
		@ViewModelKey(ContactListViewModel.class)
		ViewModel bindForumListViewModel(
				ContactListViewModel contactListViewModel);
	}


}
