package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP_1;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP_1;
import static net.sf.briar.api.messaging.Rating.BAD;
import static net.sf.briar.api.messaging.Rating.GOOD;
import static net.sf.briar.api.messaging.Rating.UNRATED;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.widgets.HorizontalBorder;
import net.sf.briar.android.widgets.HorizontalSpace;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchMessageException;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Rating;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.inject.Inject;

public class ReadPrivateMessageActivity extends BriarActivity
implements OnClickListener {

	static final int RESULT_REPLY = RESULT_FIRST_USER;
	static final int RESULT_PREV = RESULT_FIRST_USER + 1;
	static final int RESULT_NEXT = RESULT_FIRST_USER + 2;

	private static final Logger LOG =
			Logger.getLogger(ReadPrivateMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	private ContactId contactId = null;
	private Rating rating = UNRATED;
	private boolean read;
	private ImageButton readButton = null, prevButton = null, nextButton = null;
	private ImageButton replyButton = null;
	private TextView content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	private volatile MessageId messageId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int id = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);
		String contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		String authorName = i.getStringExtra("net.sf.briar.AUTHOR_NAME");
		if(authorName == null) throw new IllegalStateException();
		String r = i.getStringExtra("net.sf.briar.RATING");
		if(r == null) throw new IllegalStateException();
		rating = Rating.valueOf(r);
		byte[] b = i.getByteArrayExtra("net.sf.briar.MESSAGE_ID");
		if(b == null) throw new IllegalStateException();
		messageId = new MessageId(b);
		String contentType = i.getStringExtra("net.sf.briar.CONTENT_TYPE");
		if(contentType == null) throw new IllegalStateException();
		long timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);
		if(timestamp == -1) throw new IllegalStateException();

		if(state != null && bundleEncrypter.decrypt(state)) {
			read = state.getBoolean("net.sf.briar.READ");
		} else {
			read = false;
			setReadInDatabase(true);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		ScrollView scrollView = new ScrollView(this);
		// Give me all the width and all the unused height
		scrollView.setLayoutParams(MATCH_WRAP_1);

		LinearLayout message = new LinearLayout(this);
		message.setOrientation(VERTICAL);
		Resources res = getResources();
		message.setBackgroundColor(res.getColor(R.color.content_background));

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		ImageView thumb = new ImageView(this);
		thumb.setPadding(10, 10, 10, 10);
		if(rating == GOOD) thumb.setImageResource(R.drawable.rating_good);
		else if(rating == BAD) thumb.setImageResource(R.drawable.rating_bad);
		else thumb.setImageResource(R.drawable.rating_unrated);
		header.addView(thumb);

		TextView author = new TextView(this);
		// Give me all the unused width
		author.setLayoutParams(WRAP_WRAP_1);
		author.setTextSize(18);
		author.setMaxLines(1);
		author.setPadding(0, 10, 10, 10);
		author.setText(authorName);
		header.addView(author);

		TextView date = new TextView(this);
		date.setTextSize(14);
		date.setPadding(0, 10, 10, 10);
		long now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(timestamp, now, SHORT, SHORT));
		header.addView(date);
		message.addView(header);

		if(contentType.equals("text/plain")) {
			// Load and display the message body
			content = new TextView(this);
			content.setPadding(10, 0, 10, 10);
			message.addView(content);
			loadMessageBody();
		}
		scrollView.addView(message);
		layout.addView(scrollView);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);

		readButton = new ImageButton(this);
		readButton.setBackgroundResource(0);
		if(read) readButton.setImageResource(R.drawable.content_unread);
		else readButton.setImageResource(R.drawable.content_read);
		readButton.setOnClickListener(this);
		footer.addView(readButton);
		footer.addView(new HorizontalSpace(this));

		prevButton = new ImageButton(this);
		prevButton.setBackgroundResource(0);
		prevButton.setImageResource(R.drawable.navigation_previous_item);
		prevButton.setOnClickListener(this);
		footer.addView(prevButton);
		footer.addView(new HorizontalSpace(this));

		nextButton = new ImageButton(this);
		nextButton.setBackgroundResource(0);
		nextButton.setImageResource(R.drawable.navigation_next_item);
		nextButton.setOnClickListener(this);
		footer.addView(nextButton);
		footer.addView(new HorizontalSpace(this));

		replyButton = new ImageButton(this);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply);
		replyButton.setOnClickListener(this);
		footer.addView(replyButton);
		layout.addView(footer);

		setContentView(layout);

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void setReadInDatabase(final boolean read) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					db.setReadFlag(messageId, read);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Setting flag took " + duration + " ms");
					setReadInUi(read);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void setReadInUi(final boolean read) {
		runOnUiThread(new Runnable() {
			public void run() {
				ReadPrivateMessageActivity.this.read = read;
				if(read) readButton.setImageResource(R.drawable.content_unread);
				else readButton.setImageResource(R.drawable.content_read);
			}
		});
	}

	private void loadMessageBody() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					byte[] body = db.getMessageBody(messageId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					final String text = new String(body, "UTF-8");
					runOnUiThread(new Runnable() {
						public void run() {
							content.setText(text);
						}
					});
				} catch(NoSuchMessageException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Message removed");
					finishOnUiThread();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		state.putBoolean("net.sf.briar.READ", read);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == readButton) {
			setReadInDatabase(!read);
		} else if(view == prevButton) {
			setResult(RESULT_PREV);
			finish();
		} else if(view == nextButton) {
			setResult(RESULT_NEXT);
			finish();
		} else if(view == replyButton) {
			Intent i = new Intent(this, WritePrivateMessageActivity.class);
			i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
			i.putExtra("net.sf.briar.PARENT_ID", messageId.getBytes());
			startActivity(i);
			setResult(RESULT_REPLY);
			finish();
		}
	}
}
