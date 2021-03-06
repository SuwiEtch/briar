package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiExceptionHandler;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextSendController;
import org.briarproject.briar.android.view.TextSendController.SendListener;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.blog.BasePostFragment.POST_ID;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_TEXT_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ReblogFragment extends BaseFragment implements SendListener {

	public static final String TAG = ReblogFragment.class.getName();

	private ViewHolder ui;
	private BlogPostItem item;

	@Inject
	FeedController feedController;

	static ReblogFragment newInstance(GroupId groupId, MessageId messageId) {
		ReblogFragment f = new ReblogFragment();

		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		args.putByteArray(POST_ID, messageId.getBytes());
		f.setArguments(args);

		return f;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		Bundle args = requireArguments();
		GroupId blogId =
				new GroupId(requireNonNull(args.getByteArray(GROUP_ID)));
		MessageId postId =
				new MessageId(requireNonNull(args.getByteArray(POST_ID)));

		View v = inflater.inflate(R.layout.fragment_reblog, container, false);
		ui = new ViewHolder(v);
		ui.post.setTransitionName(postId);
		TextSendController sendController =
				new TextSendController(ui.input, this, true);
		ui.input.setSendController(sendController);
		ui.input.setReady(false);
		ui.input.setMaxTextLength(MAX_BLOG_POST_TEXT_LENGTH);
		showProgressBar();

		feedController.loadBlogPost(blogId, postId,
				new UiResultExceptionHandler<BlogPostItem, DbException>(
						this) {
					@Override
					public void onResultUi(BlogPostItem result) {
						item = result;
						bindViewHolder();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});

		return v;
	}

	private void bindViewHolder() {
		if (item == null) return;

		hideProgressBar();

		ui.post.bindItem(item);
		ui.post.hideReblogButton();

		ui.input.setReady(true);
		ui.scrollView.post(() -> ui.scrollView.fullScroll(FOCUS_DOWN));
	}

	@Override
	public void onSendClick(@Nullable String text,
			List<AttachmentHeader> headers) {
		ui.input.hideSoftKeyboard();
		feedController.repeatPost(item, text,
				new UiExceptionHandler<DbException>(this) {
					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
		finish();
	}

	private void showProgressBar() {
		ui.progressBar.setVisibility(VISIBLE);
		ui.input.setVisibility(GONE);
	}

	private void hideProgressBar() {
		ui.progressBar.setVisibility(INVISIBLE);
		ui.input.setVisibility(VISIBLE);
	}

	private class ViewHolder {

		private final ScrollView scrollView;
		private final ProgressBar progressBar;
		private final BlogPostViewHolder post;
		private final TextInputView input;

		private ViewHolder(View v) {
			scrollView = v.findViewById(R.id.scrollView);
			progressBar = v.findViewById(R.id.progressBar);
			post = new BlogPostViewHolder(v.findViewById(R.id.postLayout),
					true, new OnBlogPostClickListener() {
				@Override
				public void onBlogPostClick(BlogPostItem post) {
					// do nothing
				}

				@Override
				public void onAuthorClick(BlogPostItem post) {
					// probably don't want to allow author clicks here
				}
			}, getFragmentManager());
			input = v.findViewById(R.id.inputText);
		}
	}
}
